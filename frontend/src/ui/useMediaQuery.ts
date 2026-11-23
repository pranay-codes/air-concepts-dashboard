import { useEffect, useState } from "react";

function mediaQuery(query: string): MediaQueryList | null {
  return typeof window !== "undefined" && typeof window.matchMedia === "function"
    ? window.matchMedia(query)
    : null;
}

/** Tracks a CSS media query. Without matchMedia support it reports false, so the desktop layout is used. */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => mediaQuery(query)?.matches ?? false);
  useEffect(() => {
    const list = mediaQuery(query);
    if (!list) return;
    const update = () => setMatches(list.matches);
    list.addEventListener("change", update);
    return () => list.removeEventListener("change", update);
  }, [query]);
  return matches;
}
