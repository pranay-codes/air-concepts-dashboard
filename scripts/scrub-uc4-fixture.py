"""Copy the UC4 fixture, blank only approved personal-data cells, and remove orphaned shared names.

Preserves the original file and all unrelated ZIP parts. Prints cell addresses and hashes only.
"""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/ACSL Cashflow Forecast Template - Cleaned.xlsx"
DEST = ROOT / "backend/src/test/resources/forecast/template-v1.xlsx"
NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
ET.register_namespace("", NS["m"])

def main():
    original = SOURCE.read_bytes()
    before_hash = hashlib.sha256(original).hexdigest()
    changed, name_indices = [], set()
    with ZipFile(SOURCE) as source:
        book = ET.fromstring(source.read("xl/workbook.xml"))
        sheet = next(s for s in book.find("m:sheets", NS) if s.attrib["name"] == "Staffing")
        links = ET.fromstring(source.read("xl/_rels/workbook.xml.rels"))
        target = next(r.attrib["Target"] for r in links if r.attrib["Id"] == sheet.attrib[f"{{{REL}}}id"])
        path = target.lstrip("/") if target.startswith("/") else "xl/" + target
        grid = ET.fromstring(source.read(path))
        targets = {f"{col}{row}" for row in list(range(10, 16)) + list(range(23, 29)) for col in "ABCD"}
        for cell in grid.findall(".//m:c", NS):
            if cell.attrib["r"] not in targets:
                continue
            contents = [child for child in cell if child.tag.rsplit("}", 1)[-1] in {"v", "f", "is"}]
            if not contents:
                continue
            if cell.attrib["r"][0] in "AB" and cell.attrib.get("t") == "s":
                value = cell.find("m:v", NS)
                if value is not None:
                    name_indices.add(int(value.text))
            for child in contents:
                cell.remove(child)
            cell.attrib.pop("t", None)
            changed.append(cell.attrib["r"])
        replacements = {path: ET.tostring(grid, encoding="utf-8", xml_declaration=True)}
        if "xl/sharedStrings.xml" in source.namelist() and name_indices:
            strings = ET.fromstring(source.read("xl/sharedStrings.xml"))
            for index, item in enumerate(strings):
                if index in name_indices:
                    item.clear()
                    ET.SubElement(item, f"{{{NS['m']}}}t").text = ""
            replacements["xl/sharedStrings.xml"] = ET.tostring(strings, encoding="utf-8", xml_declaration=True)
        DEST.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(DEST, "w", ZIP_DEFLATED) as dest:
            for item in source.infolist():
                dest.writestr(item, replacements.get(item.filename, source.read(item.filename)))
    assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == before_hash
    report = {"changedCells": ["Staffing!" + cell for cell in changed], "clearedSharedStringEntries": len(name_indices),
              "sourceSha256": before_hash, "fixtureSha256": hashlib.sha256(DEST.read_bytes()).hexdigest()}
    (ROOT / "docs/plans/UC4-fixture-scrub.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))

if __name__ == "__main__":
    main()
