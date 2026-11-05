"""INF-002 offline safety contracts; no AWS credentials or network required."""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def evaluate(value, parameters, mappings, parameter_types=None):
    """Evaluate the documented rule-function subset, without contacting AWS.

    parameter_types maps an AWS parameter type to the logical IDs declared with it.
    Fn::ValueOfAll takes a type, not an ID, so it needs this map.
    """
    parameter_types = parameter_types or {}
    if isinstance(value, list):
        return [evaluate(v, parameters, mappings, parameter_types) for v in value]
    if not isinstance(value, dict):
        return value
    key, argument = next(iter(value.items()))
    if key == 'Ref':
        return parameters[argument]
    args = evaluate(argument, parameters, mappings, parameter_types)
    functions = {
        'Fn::Equals': lambda a: a[0] == a[1],
        'Fn::Not': lambda a: not a[0],
        'Fn::And': all,
        'Fn::Or': any,
        'Fn::Contains': lambda a: a[1] in a[0],
        'Fn::EachMemberEquals': lambda a: all(member == a[1] for member in a[0]),
        'Fn::FindInMap': lambda a: mappings[a[0]][a[1]][a[2]],
        'Fn::ValueOf': lambda a: parameters[a[0] + '.' + a[1]],
        'Fn::ValueOfAll': lambda a: [parameters[name + '.' + a[1]]
                                     for name in parameter_types.get(a[0], [])],
    }
    return functions[key](args)


class InfrastructureTests(unittest.TestCase):
    def setUp(self):
        template = ROOT / 'infra/cloudformation/infrastructure.json'
        self.assertTrue(template.exists(), 'INF-002 shared template must exist')
        self.template = json.loads(template.read_text())
        self.resources = self.template['Resources']

    def test_INF002_environment_defaults(self):
        mapping = self.template['Mappings']['Sizing']
        self.assertEqual(mapping['test']['InstanceType'], 't4g.micro')
        self.assertEqual(mapping['live']['InstanceType'], 't4g.small')
        self.assertEqual(int(mapping['test']['RootVolumeSize']), 10)
        self.assertEqual(int(mapping['live']['RootVolumeSize']), 20)
        for entry in mapping.values():
            self.assertTrue(all(isinstance(value, (str, list)) for value in entry.values()),
                            'CloudFormation mapping values must be strings or lists')
        for environment in ('test', 'live'):
            params = json.loads((ROOT / f'infra/params/{environment}.example.json').read_text())
            values = {p['ParameterKey']: p['ParameterValue'] for p in params}
            self.assertEqual(values['Environment'], environment)
            self.assertEqual(values['InstanceType'], 'environment-default')
            self.assertEqual(values['RootVolumeSize'], '0')
            self.assertTrue(set(values) <= self.template['Parameters'].keys())

    def test_INF002_ami_is_pinned_not_floating(self):
        ami = self.template['Parameters']['AmiId']
        self.assertEqual(ami['Type'], 'AWS::EC2::Image::Id')
        self.assertNotIn('Default', ami, 'AmiId must not default to a moving lookup')
        for environment in ('test', 'live'):
            params = json.loads((ROOT / f'infra/params/{environment}.example.json').read_text())
            values = {p['ParameterKey']: p['ParameterValue'] for p in params}
            self.assertIn('AmiId', values)
            self.assertNotIn('/aws/service/', values['AmiId'])

    def test_INF002_tags_and_names_are_consistent(self):
        expected = {'client': 'air-concepts', 'environment': {'Ref': 'Environment'},
                    'project': 'air-concepts', 'requirement': 'INF-002'}
        tag_blocks = []
        for name, resource in self.resources.items():
            props = resource.get('Properties', {})
            if 'Tags' in props:
                tag_blocks.append((name, props['Tags']))
            for spec in props.get('TagSpecifications', []):
                tag_blocks.append((f'{name}/{spec["ResourceType"]}', spec['Tags']))
            for spec in props.get('LaunchTemplateData', {}).get('TagSpecifications', []):
                tag_blocks.append((f'{name}/launch:{spec["ResourceType"]}', spec['Tags']))
        self.assertGreater(len(tag_blocks), 10)
        for where, tags in tag_blocks:
            by_key = {tag['Key']: tag['Value'] for tag in tags}
            for key, value in expected.items():
                self.assertEqual(by_key.get(key), value, f'{where} must carry {key}={value}')
            name = by_key.get('Name')
            if where in ('AirConceptsBackend', 'AirConceptsApiLaunchTemplate/launch:instance'):
                # The backend host is named for its role, not its environment.
                self.assertEqual(name, 'air-concepts-backend', f'{where} must be named air-concepts-backend')
            else:
                self.assertIsInstance(name, dict, f'{where} needs a Name tag')
                self.assertTrue(name['Fn::Sub'].startswith('air-concepts-${Environment}-'),
                                f'{where} Name must follow air-concepts-<environment>-<component>')
            self.assertEqual(set(by_key) - set(expected) - {'Name'}, set(),
                             f'{where} has unexpected tag keys')

    def test_INF002_host_hardening(self):
        host = self.resources['AirConceptsBackend']['Properties']
        launch = self.resources['AirConceptsApiLaunchTemplate']['Properties']['LaunchTemplateData']
        self.assertEqual(launch['MetadataOptions']['HttpTokens'], 'required')
        self.assertEqual(launch['MetadataOptions']['HttpPutResponseHopLimit'], 1)
        self.assertNotIn('KeyName', host)
        disk = host['BlockDeviceMappings'][0]['Ebs']
        self.assertTrue(disk['Encrypted'])
        self.assertEqual(disk['VolumeType'], 'gp3')
        rules = self.resources['AirConceptsApiSecurityGroup']['Properties']['SecurityGroupIngress']
        self.assertEqual({r['FromPort'] for r in rules}, {80, 443})
        self.assertEqual(host['CreditSpecification']['CPUCredits'], {'Ref': 'CpuCredits'})
        self.assertIn('CreationPolicy', self.resources['AirConceptsBackend'])

    def test_INF002_private_frontend(self):
        bucket = self.resources['AirConceptsFrontendBucket']['Properties']
        self.assertTrue(all(bucket['PublicAccessBlockConfiguration'].values()))
        self.assertNotIn('WebsiteConfiguration', bucket)
        statements = self.resources['AirConceptsFrontendBucketPolicy']['Properties']['PolicyDocument']['Statement']
        allow = next(s for s in statements if s['Effect'] == 'Allow')
        self.assertEqual(allow['Principal'], {'Service': 'cloudfront.amazonaws.com'})
        self.assertIn('AWS:SourceArn', allow['Condition']['StringEquals'])
        config = self.resources['AirConceptsFrontendDistribution']['Properties']['DistributionConfig']
        self.assertNotIn('CustomErrorResponses', config)
        self.assertEqual(config['DefaultCacheBehavior']['ViewerProtocolPolicy'], 'redirect-to-https')

    def test_INF002_environment_iam_boundary(self):
        policies = self.resources['AirConceptsApiRole']['Properties']['Policies']
        statements = policies[0]['PolicyDocument']['Statement']
        read = next(s for s in statements if s['Sid'] == 'ReadBackendParameters')
        self.assertEqual(read['Resource']['Fn::Sub'], 'arn:${AWS::Partition}:ssm:${AWS::Region}:${AWS::AccountId}:parameter/air-concepts/${Environment}/backend/*')
        deny = next(s for s in statements if s['Sid'] == 'DenyOtherEnvironment')
        self.assertEqual(deny['Effect'], 'Deny')
        self.assertIn('OppositeEnvironment', deny['Resource']['Fn::Sub'][1])
        boundary = next((s for s in statements if s.get('Sid') == 'DenyParameterReadsOutsideBackend'), {})
        self.assertTrue(boundary, 'SSM managed policy must not broaden application parameter reads')
        self.assertEqual(boundary['Effect'], 'Deny')
        self.assertEqual(boundary['NotResource'], read['Resource'])
        self.assertEqual(self.resources['AirConceptsBackendRepository']['Properties']['ImageTagMutability'], 'IMMUTABLE')
        lifecycle = json.loads(self.resources['AirConceptsBackendRepository']['Properties']['LifecyclePolicy']['LifecyclePolicyText'])
        self.assertEqual(lifecycle['rules'][0]['selection']['tagStatus'], 'untagged')
        self.assertEqual(self.resources['AirConceptsBackendRepository']['Properties']['EmptyOnDelete'], True)

    def test_INF002_bootstrap_logs_each_step(self):
        script = self.resources['AirConceptsBackend']['Properties']['UserData']['Fn::Base64']['Fn::Sub']
        self.assertIn('/var/log/air-concepts-bootstrap.log', script)
        self.assertIn('set -o errtrace', script)
        self.assertIn("trap 'on_error", script)
        for step in range(1, 8):
            self.assertRegex(script, rf'step "{step}/7 ')

    def test_INF002_every_resource_is_deleted_with_the_stack(self):
        # Policy: stack deletion removes every resource. No resource may be retained.
        for name, resource in self.resources.items():
            self.assertEqual(resource.get('DeletionPolicy'), 'Delete', f'{name} must have DeletionPolicy Delete')
            self.assertNotEqual(resource.get('UpdateReplacePolicy'), 'Retain', f'{name} must not retain on replacement')

    def test_INF002_configuration_guards(self):
        rules = self.template['Rules']
        self.assertIn('SydneyOnly', rules)
        self.assertIn('CustomFrontendCertificate', rules)
        self.assertIn('LiveFrontendDomain', rules)
        self.assertIn('TestInstanceSize', rules)
        self.assertIn('LiveInstanceSize', rules)
        self.assertIn('ExistingRepositoryUri', self.template['Conditions'])

    def configuration_errors(self, **overrides):
        parameters = {k: v.get('Default', '') for k, v in self.template['Parameters'].items()}
        parameters.update({'ExpectedAccountId': '123456789012', 'AWS::AccountId': '123456789012',
                           'AWS::Region': 'ap-southeast-2', 'VpcId': 'vpc-example',
                           'PublicSubnetId.VpcId': 'vpc-example'})
        parameters.update(overrides)
        parameter_types = {}
        for pname, spec in self.template['Parameters'].items():
            parameter_types.setdefault(spec['Type'], []).append(pname)
        errors = []
        for name, rule in self.template['Rules'].items():
            if not evaluate(rule.get('RuleCondition', True), parameters, self.template['Mappings'],
                            parameter_types):
                continue
            for check in rule['Assertions']:
                if not evaluate(check['Assert'], parameters, self.template['Mappings'],
                                parameter_types):
                    errors.append(name)
        return errors

    def test_INF002_invalid_environment_combinations_rejected(self):
        self.assertEqual(self.configuration_errors(), [])
        cases = [({'Environment': 'live'}, 'LiveFrontendDomain'),
                 ({'Environment': 'test', 'InstanceType': 't4g.medium'}, 'TestInstanceSize'),
                 ({'Environment': 'live', 'InstanceType': 't4g.micro'}, 'LiveInstanceSize'),
                 ({'Environment': 'live', 'RootVolumeSize': 10}, 'LiveInstanceSize'),
                 ({'FrontendHostname': 'app.example.com'}, 'CustomFrontendCertificate'),
                 ({'FrontendCertificateArn': 'certificate'}, 'CustomFrontendCertificate'),
                 ({'AWS::Region': 'us-east-1'}, 'SydneyOnly'),
                 ({'ExpectedAccountId': '999999999999'}, 'ExpectedAccount'),
                 ({'PublicSubnetId.VpcId': 'vpc-other'}, 'SubnetVpc')]
        for values, expected in cases:
            with self.subTest(values=values):
                self.assertIn(expected, self.configuration_errors(**values))
        self.assertEqual(self.configuration_errors(Environment='live', FrontendHostname='app.example.com',
                                                   FrontendCertificateArn='certificate'), [])

    def test_INF002_bootstrap_preserves_failure_exit_status(self):
        script = self.resources['AirConceptsBackend']['Properties']['UserData']['Fn::Base64']['Fn::Sub']
        self.assertNotIn('rm -rf "$work_dir"; signal_result', script)
        self.assertIn('result=$?', script)

    def test_INF002_bootstrap_checksum_selects_the_binary_line(self):
        # Compose publishes checksums in binary mode: '<sha256> *<name>'. The bootstrap must select exactly the
        # binary's line. A pattern requiring a space before the name matches nothing, so step 5 failed on every run.
        script = self.resources['AirConceptsBackend']['Properties']['UserData']['Fn::Base64']['Fn::Sub']
        match = re.search(r"grep -E '([^']+)' checksums\.txt \| sha256sum --check -", script)
        self.assertIsNotNone(match, 'bootstrap must verify the binary with grep -E and sha256sum --check')
        published = (
            'd26373b19e89160546d15407516cc59f453030d9bc5b43ba7faf16f7b4980137 *docker-compose-linux-aarch64\n'
            '37cb906c78c4adefb76e2dcbe48bcd7d8e8cf18c85c4b91eabd152f88228b2b4 *docker-compose-linux-aarch64.provenance.json\n'
            '39be4fb44c072228402f348a3cb1ff02e465c1abad0be41d9ac876efbaaadf9f *docker-compose-linux-aarch64.sbom.json\n'
        )
        selected = [line for line in published.splitlines() if re.search(match.group(1), line)]
        self.assertEqual(selected, [
            'd26373b19e89160546d15407516cc59f453030d9bc5b43ba7faf16f7b4980137 *docker-compose-linux-aarch64'])

    def test_INF002_spa_routes_keep_missing_assets_as_errors(self):
        code = self.resources['AirConceptsFrontendRoutes']['Properties']['FunctionCode']
        cases = {'/': '/index.html', '/invoices/123': '/index.html',
                 '/assets/missing.js': '/assets/missing.js', '/assets/missing': '/assets/missing',
                 '/missing.css': '/missing.css', '/favicon.ico': '/favicon.ico'}
        javascript = code + '\nconst cases = ' + json.dumps(cases) + ''';
        for (const [uri, expected] of Object.entries(cases)) {
          if (handler({request: {method: 'GET', uri}}).uri !== expected) process.exit(1);
        }
        if (handler({request: {method: 'POST', uri: '/invoices'}}).uri !== '/invoices') process.exit(1);
        '''
        result = subprocess.run(['node', '-e', javascript], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_INF002_compose_isolation(self):
        # Validate actual Compose semantics, using synthetic data in a temporary copy.
        with tempfile.TemporaryDirectory(dir=ROOT / 'infra') as staging:
            directory = Path(staging)
            shutil.copy(ROOT / 'infra/deploy/compose.yaml', directory / 'compose.yaml')
            (directory / 'runtime').mkdir()
            password = 'synthetic-$UNDEFINED-${UNDEFINED}-"quoted"-\'single\''
            (directory / 'runtime/backend.env').write_text('DATABASE_PASSWORD=' + password + '\n')
            (directory / 'docker-config').mkdir()
            env = dict(os.environ, DOCKER_CONFIG=str(directory / 'docker-config'),
                       ENVIRONMENT='test', BACKEND_REPOSITORY_URI='example.invalid/backend',
                       BACKEND_IMAGE_DIGEST='a' * 64, CADDY_IMAGE_DIGEST='b' * 64,
                       API_HOSTNAME='api.example.invalid')
            version = subprocess.run(['docker', 'compose', 'version', '--short'], env=env,
                                     capture_output=True, text=True)
            self.assertEqual(version.returncode, 0, version.stderr)
            match = re.search(r'(\d+)\.(\d+)\.(\d+)', version.stdout)
            self.assertIsNotNone(match, version.stdout)
            if tuple(map(int, match.groups())) < (2, 30, 0):
                self.skipTest('Compose >=2.30 required for raw secret files; bootstrap pins v2.40.3')
            result = subprocess.run(['docker', 'compose', '-f', str(directory / 'compose.yaml'),
                                     'config', '--format', 'json'], env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            config = json.loads(result.stdout)
            api, caddy = config['services']['api'], config['services']['caddy']
            self.assertNotIn('ports', api)
            self.assertEqual(api['user'], '10001:10001')
            self.assertTrue(api['read_only'])
            self.assertEqual(api['platform'], 'linux/arm64')
            # `config` escapes '$' as '$$' so its output can be re-interpolated. The container receives the
            # literal value; the 'Prove the container receives literal env_file values' CI step checks that.
            self.assertEqual(api['environment']['DATABASE_PASSWORD'].replace('$$', '$'), password)
            self.assertEqual({p['published'] for p in caddy['ports']}, {'80', '443'})
            self.assertTrue(config['networks']['api-internal']['internal'])
            self.assertIn('api-egress', api['networks'])
            self.assertRegex(api['image'], r'@sha256:[a-f0-9]{64}$')
            del env['BACKEND_IMAGE_DIGEST']
            rejected = subprocess.run(['docker', 'compose', '-f', str(directory / 'compose.yaml'),
                                       'config', '--quiet'], env=env, capture_output=True, text=True)
            self.assertNotEqual(rejected.returncode, 0)

    def test_INF002_compose_requires_raw_secret_files(self):
        # Raw format prevents Compose from interpolating dollars or stripping quotes.
        self.assertIn('format: raw', (ROOT / 'infra/deploy/compose.yaml').read_text())

    def test_INF002_ci_pins_the_compose_release_used_by_bootstrap(self):
        # CI must exercise the same Compose release as the host bootstrap, not whatever the runner ships.
        version = self.template['Parameters']['ComposeVersion']['Default']
        workflow = (ROOT / '.github/workflows/infrastructure.yml').read_text()
        self.assertIn(f'COMPOSE_VERSION: {version}', workflow)
        self.assertIn('docker-compose-linux-x86_64', workflow)
        self.assertIn('sha256sum --check', workflow)
        self.assertIn('/usr/local/lib/docker/cli-plugins/docker-compose', workflow)

    def test_INF002_ci_proves_the_container_receives_literal_secret_values(self):
        # Compose config output escapes '$'; only a running container shows the value the API will receive.
        workflow = (ROOT / '.github/workflows/infrastructure.yml').read_text()
        self.assertIn('format: raw', workflow)
        self.assertIn('["printenv", "DATABASE_PASSWORD"]', workflow)
        self.assertIn('cmp expected.txt actual.txt', workflow)


if __name__ == '__main__':
    unittest.main()
