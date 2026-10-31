#!/usr/bin/env python3
"""Compile src/rules/block.json and allow.json (Chrome DNR format) into the compact
rules.txt the Android app loads in the background.

Run from anywhere:  python3 android/tools/compile_rules.py

Line formats (tab separated):
  D<TAB>domain                                   domain blocked everywhere
  T<TAB>domain                                   domain blocked when third-party only
  B|A<TAB>token<TAB>flags<TAB>inits<TAB>excl<TAB>pattern
      B = block rule, A = allow (exception) rule
      token = rarest safe token of the pattern (index key), may be empty
      flags = n (any) | t (third-party only) | f (first-party only)
      inits / excl = comma separated initiator domains (page must / must not be one of them)
      pattern = lowercase ABP-style filter: || | * ^ supported
Redirect rules and main_frame-only rules are skipped.
"""
import collections
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "android/app/src/main/assets/rules/rules.txt"

SAFE_SEPARATORS = set("|/.:?&=_-^,;~@!#%+")


def candidates(p):
    """Tokens that can only match whole URL tokens (so a token index never misses a hit)."""
    anchored = p.startswith("|")
    out = []
    for m in re.finditer(r"[a-z0-9]+", p):
        s, e = m.span()
        tok = m.group()
        if len(tok) < 3:
            continue
        left_ok = (s > 0 and p[s - 1] in SAFE_SEPARATORS) or (s == 0 and anchored)
        right_ok = e < len(p) and p[e] in SAFE_SEPARATORS
        if left_ok and right_ok:
            out.append(tok)
    return out


def flags_of(cond):
    return {"thirdParty": "t", "firstParty": "f"}.get(cond.get("domainType"), "n")


def collect():
    domains, third_domains, rules = set(), set(), set()
    for name, kind in (("block.json", "B"), ("allow.json", "A")):
        want = "block" if kind == "B" else "allow"
        for r in json.load(open(ROOT / "src/rules" / name)):
            if r["action"]["type"] != want:
                continue
            c = r["condition"]
            if c.get("resourceTypes") == ["main_frame"]:
                continue
            inits = ",".join(c.get("initiatorDomains", [])).lower()
            excl = ",".join(c.get("excludedInitiatorDomains", [])).lower()
            flags = flags_of(c)
            if "requestDomains" in c and "urlFilter" not in c:
                for d in c["requestDomains"]:
                    d = d.lower()
                    if kind == "B" and not inits and not excl and flags != "f":
                        (third_domains if flags == "t" else domains).add(d)
                    else:
                        rules.add((kind, flags, inits, excl, "||" + d + "^"))
            elif "urlFilter" in c:
                p = c["urlFilter"].lower()
                # "$csp=..." style option text can never match a URL.
                if "\t" in p or "\n" in p or "$" in p:
                    continue
                rules.add((kind, flags, inits, excl, p))
    third_domains -= domains
    return domains, third_domains, rules


def main():
    domains, third_domains, rules = collect()
    freq = collections.Counter()
    cands = {}
    for rule in rules:
        c = candidates(rule[4])
        cands[rule] = c
        freq.update(set(c))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    generic = 0
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        for d in sorted(domains):
            f.write(f"D\t{d}\n")
        for d in sorted(third_domains):
            f.write(f"T\t{d}\n")
        for rule in sorted(rules):
            kind, flags, inits, excl, pattern = rule
            c = cands[rule]
            token = min(c, key=lambda t: (freq[t], -len(t))) if c else ""
            if not token:
                generic += 1
            f.write(f"{kind}\t{token}\t{flags}\t{inits}\t{excl}\t{pattern}\n")

    print(f"domains={len(domains)} third_party_domains={len(third_domains)} pattern_rules={len(rules)} "
          f"without_token={generic} size={OUT.stat().st_size // 1024}KB")


if __name__ == "__main__":
    main()
