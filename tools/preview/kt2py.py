# -*- coding: utf-8 -*-
"""Mechanical translator for the simple Kotlin subset used by Vehicles.kt model
functions, so the geometry can be previewed outside Android."""
import re


def grab_fun(src, name):
    m = re.search(r'fun ' + name + r'\s*\(', src)
    if not m:
        raise KeyError(name)
    i = m.end()
    depth = 1
    while depth:
        if src[i] == '(':
            depth += 1
        elif src[i] == ')':
            depth -= 1
        i += 1
    sig = src[m.end():i - 1]
    while src[i] != '{':
        i += 1
    j = i + 1
    depth = 1
    while depth:
        if src[j] == '{':
            depth += 1
        elif src[j] == '}':
            depth -= 1
        j += 1
    return sig, src[i + 1:j - 1]


def params(sig):
    out, depth, cur = [], 0, ''
    for ch in sig:
        if ch in '(<': depth += 1
        if ch in ')>': depth -= 1
        if ch == ',' and depth == 0:
            out.append(cur); cur = ''
        else:
            cur += ch
    if cur.strip(): out.append(cur)
    names = []
    for p in out:
        p = p.strip()
        if not p: continue
        names.append(p.split(':')[0].strip())
    return names


def _coerce(s):
    """`expr.coerceIn(a,b)` where expr may end in a parenthesised group."""
    names = {'coerceIn': 'clamp', 'coerceAtLeast': 'max', 'coerceAtMost': 'min'}
    changed = True
    while changed:
        changed = False
        for kt, py in names.items():
            m = re.search(r'\)\.' + kt + r'\(', s)
            if not m:
                continue
            close = m.start()
            depth = 0
            i = close
            while i >= 0:
                if s[i] == ')':
                    depth += 1
                elif s[i] == '(':
                    depth -= 1
                    if depth == 0:
                        break
                i -= 1
            start = i
            while start > 0 and (s[start - 1].isalnum() or s[start - 1] in '._'):
                start -= 1
            recv = s[start:close + 1]
            j = m.end()
            depth = 1
            while j < len(s) and depth:
                if s[j] == '(':
                    depth += 1
                elif s[j] == ')':
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            args = s[m.end():j]
            s = s[:start] + py + '(' + recv + ', ' + args + ')' + s[j + 1:]
            changed = True
    return s


def _ternary(s):
    """Rewrite Kotlin `if (c) a else b` expressions into Python conditionals."""
    i = 0
    while True:
        j = s.find('if (', i)
        if j < 0:
            return s
        if j == 0 or s[:j].strip() == '' or s[:j].rstrip().endswith(')') and s.lstrip().startswith('if ('):
            # statement-level if is handled by the line converter
            if s.lstrip().startswith('if (') and j == len(s) - len(s.lstrip()):
                i = j + 4
                continue
        k = j + 3
        depth = 0
        while k < len(s):
            if s[k] == '(':
                depth += 1
            elif s[k] == ')':
                depth -= 1
                if depth == 0:
                    break
            k += 1
        cond = s[j + 4:k]
        rest = s[k + 1:]
        d = 0
        pos = 0
        split = -1
        while pos < len(rest):
            ch = rest[pos]
            if ch in '([':
                d += 1
            elif ch in ')]':
                if d == 0:
                    break
                d -= 1
            elif d == 0 and rest.startswith(' else ', pos):
                split = pos
                break
            pos += 1
        if split < 0:
            i = j + 4
            continue
        a = rest[:split]
        tail = rest[split + 6:]
        d = 0
        pos = 0
        end = len(tail)
        while pos < len(tail):
            ch = tail[pos]
            if ch in '([':
                d += 1
            elif ch in ')]':
                if d == 0:
                    end = pos
                    break
                d -= 1
            elif ch == ',' and d == 0:
                end = pos
                break
            pos += 1
        b = tail[:end]
        s = s[:j] + '(' + a.strip() + ' if ' + cond.strip() + ' else ' + b.strip() + ')' + tail[end:]
        i = 0


def tokens(line):
    s = line
    s = re.sub(r'//.*', '', s)
    s = re.sub(r'0x([0-9A-Fa-f]{8})\.toInt\(\)', lambda m: str(int(m.group(1)[2:], 16)), s)
    s = re.sub(r'0x([0-9A-Fa-f]{6,8})', lambda m: str(int(m.group(1)[-6:], 16)), s)
    s = s.replace('.toInt()', '').replace('.toFloat()', '').replace('.toDouble()', '')
    s = re.sub(r'(\d)f\b', r'\1', s)
    s = re.sub(r'\bval\s+', '', s)
    s = re.sub(r'\bvar\s+', '', s)
    if not s.strip().startswith('if (') and not s.strip().startswith('} '):
        s = _ternary(s)
    for _ in range(3):
        s = re.sub(r'(?:floatArrayOf|intArrayOf)\(([^()]*)\)', r'[\1]', s)
    s = s.replace('Math.PI', 'math.pi').replace('PI', 'math.pi')
    s = s.replace('Theme.shade(', 'shade(')
    s = s.replace('Theme.mix(', 'mix(')
    s = s.replace('Theme.withAlpha(', 'withAlpha(')
    s = s.replace('Theme.AMBER', '16761149').replace('Theme.RED', '14702671')
    s = s.replace('true', 'True').replace('false', 'False').replace('null', 'None')
    s = s.replace('&&', ' and ').replace('||', ' or ')
    s = re.sub(r'!(?=[A-Za-z_(])', ' not ', s)
    s = _coerce(s)
    # chained coerce helpers
    for _ in range(4):
        s = re.sub(r'([\w\.\[\]]+)\.coerceIn\(([^()]*?),\s*([^()]*?)\)', r'clamp(\1,\2,\3)', s)
        s = re.sub(r'([\w\.\[\]]+)\.coerceAtMost\(([^()]*?)\)', r'min(\1,\2)', s)
        s = re.sub(r'([\w\.\[\]]+)\.coerceAtLeast\(([^()]*?)\)', r'max(\1,\2)', s)
    return s


def logical_lines(body):
    """Join Kotlin statements that wrap across several source lines."""
    out, buf, depth = [], '', 0
    for raw in body.split('\n'):
        line = re.sub(r'//.*', '', raw).rstrip()
        if not line.strip():
            continue
        buf = (buf + ' ' + line.strip()).strip() if buf else line.strip()
        depth += line.count('(') - line.count(')')
        depth += line.count('[') - line.count(']')
        if depth <= 0:
            out.append(buf)
            buf, depth = '', 0
    if buf:
        out.append(buf)
    return out


def convert(body, indent='    '):
    out = []
    lvl = 1
    for raw in logical_lines(body):
        line = raw.strip()
        if not line or line.startswith('//') or line.startswith('*') or line.startswith('/*'):
            continue
        close = 0
        while line.startswith('}'):
            close += 1
            line = line[1:].strip()
        lvl -= close
        if lvl < 0: lvl = 0
        if line.startswith('else'):
            head = 'else:' if line.rstrip('{').strip() == 'else' else None
            if head is None:
                cond = re.match(r'else if \((.*)\)\s*\{?$', line)
                head = 'elif ' + tokens(cond.group(1)) + ':' if cond else None
            if head:
                out.append(indent * lvl + head)
                if line.endswith('{'): lvl += 1
                continue
        m = re.match(r'for \((\w+) in (.+?)\)\s*\{?$', line)
        if m:
            var, rng = m.group(1), m.group(2)
            r2 = re.match(r'(.+?) until (.+?)( step (\d+))?$', rng)
            r1 = re.match(r'(.+?)\.\.(.+?)( step (\d+))?$', rng)
            if r2:
                step = r2.group(4) or '1'
                expr = 'range(int(%s), int(%s), %s)' % (tokens(r2.group(1)), tokens(r2.group(2)), step)
            elif r1:
                step = r1.group(4) or '1'
                expr = 'range(int(%s), int(%s)+1, %s)' % (tokens(r1.group(1)), tokens(r1.group(2)), step)
            else:
                expr = tokens(rng)
            out.append(indent * lvl + 'for %s in %s:' % (var, expr))
            if line.endswith('{'): lvl += 1
            continue
        m = re.match(r'(if|while) \((.*)\)\s*\{?$', line)
        if m:
            out.append(indent * lvl + '%s %s:' % ('if' if m.group(1) == 'if' else 'while', tokens(m.group(2))))
            if line.endswith('{'): lvl += 1
            continue
        m = re.match(r'if \((.*)\) (.+)$', line)
        if m and not line.endswith('{'):
            out.append(indent * lvl + 'if %s: %s' % (tokens(m.group(1)), tokens(m.group(2))))
            continue
        opens = line.count('{') - line.count('}')
        out.append(indent * lvl + tokens(line))
        lvl += max(0, opens)
    return '\n'.join(out)


def build(src, name):
    sig, body = grab_fun(src, name)
    py = convert(body)
    code = 'def %s(%s):\n' % (name, ', '.join(params(sig)))
    code += py if py.strip() else '    pass'
    return code
