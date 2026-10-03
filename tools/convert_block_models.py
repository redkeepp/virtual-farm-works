# convert_block_models.py — 1.21.1 line only: rewrites the owner's Blockbench block models, exported with 26.1's
# multi-axis element rotations, as geometry Minecraft 1.21.1 can read, with the same look and the same textures.
"""
Why: 26.1 (Minecraft 1.21.11 and later) accepts an element rotation as Euler angles on several axes at once,
{"x": -90, "y": 0, "z": -90, "origin": [...]}, in any amount. 1.21.1 only reads {"axis", "angle", "origin"}: one
axis and an angle of 0, +-22.5 or +-45. It refuses the whole model otherwise ("Missing axis"), and the machine shows
the missing model.

How: a rotation made of quarter turns maps an axis-aligned box onto another axis-aligned box. Each such element is
written as that rotated box, without a rotation: its corners move, every face moves to the direction it faces after
the rotation and keeps its texture and UV rectangle, with the face "rotation" (0/90/180/270) that makes each corner
show the same texture point as before. Every other element, key and value stays exactly as exported (Blockbench's
outliner groups refer to elements by index, and the element order is kept).

Read from the games' own code (2026-10-02; 26.1 from its unobfuscated client jar with javap, 1.21.1 from
neoforge-21.1.251-sources.jar):
- 26.1 CuboidModelElement$Deserializer: "axis"/"angle" -> one-axis rotation, else "x"/"y"/"z" (missing = 0) ->
  EulerXYZRotation, whose transformation is Matrix4f.rotationZYX(z, y, x) = Rz * Ry * Rx (X turns first; checked by
  running JOML on the unit vectors). FaceBakery moves each corner to M * (corner - origin) + origin, origin / 16 like
  the corners; "rescale" changes nothing for quarter turns (every scale factor is 1).
- Both versions' FaceInfo (the corner order of each face) and face UV lookup (corner i with face rotation r shows
  u = uv[0] if (i + r/90) % 4 is 0 or 1, else uv[2]; v = uv[1] if it is 0 or 3, else uv[3]) are identical, and
  both take a corner's coordinates straight from "from" (min side) and "to" (max side).

Proof: before writing, the script bakes every face of the original as 26.1 does and every face of the result as
1.21.1 does (corner positions and texture points, exact decimal arithmetic) and writes nothing unless they match
corner for corner, element by element, and the result passes 1.21.1's parser limits. What stays different: inside
each quad, 1.21.1 lists the corners of an unrotated face in that face's standard order, which only changes how
per-corner light and ambient occlusion are blended across the face, never the texture.

Usage, from the repository root (Python 3, standard library only; written and run with 3.13):
    python tools/convert_block_models.py            convert every model in models/block that needs it, in place
    python tools/convert_block_models.py --check    report and verify only, write nothing
    python tools/convert_block_models.py FILE...    only these files
Files without Euler rotations are left untouched, so running it twice changes nothing. After the owner re-exports a
model on main, bring it to this branch and run the script again.
"""

import argparse
import json
import sys
from decimal import Decimal
from pathlib import Path

MODEL_DIR = Path('src/main/resources/assets/virtualfarmworks/models/block')

# Unit normal of each face, by the name model JSON uses.
DIRECTIONS = {
    'down': (0, -1, 0), 'up': (0, 1, 0),
    'north': (0, 0, -1), 'south': (0, 0, 1),
    'west': (-1, 0, 0), 'east': (1, 0, 0),
}
# Blockbench's face order, kept for the rewritten elements.
FACE_ORDER = ('north', 'east', 'south', 'west', 'up', 'down')
# FaceInfo (the same table in 26.1 and 1.21.1): the corners of each face in the order the baker walks them; for each
# corner, whether x, y and z come from "from" (0) or "to" (1).
FACE_CORNERS = {
    'down': ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
    'up': ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    'north': ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
    'south': ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
    'west': ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
    'east': ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
}
# 1.21.1's limits: BlockElement$Deserializer (coordinates, one-axis angles), BlockFaceUV$Deserializer (face turns).
COORD_MIN, COORD_MAX = Decimal(-16), Decimal(32)
LEGACY_ANGLES = {Decimal(0), Decimal('22.5'), Decimal('-22.5'), Decimal(45), Decimal(-45)}
FACE_ROTATIONS = (0, 90, 180, 270)
IDENTITY = ((1, 0, 0), (0, 1, 0), (0, 0, 1))


class ConversionError(Exception):
    """A model this script cannot convert exactly; nothing is written then."""


def load(text):
    """Parses model JSON keeping every number as an exact Decimal (as written, no binary rounding)."""
    return json.loads(text, parse_float=Decimal, parse_int=Decimal)


def is_euler(rotation):
    """26.1's own test: a rotation without "axis" and "angle" is read as x/y/z Euler angles."""
    return rotation is not None and 'axis' not in rotation and 'angle' not in rotation


def quarter_turn(angle):
    """Exact (cos, sin) of a multiple of 90 degrees."""
    angle = Decimal(angle)
    if angle % 90 != 0:
        raise ConversionError(f'angle {angle} is not a multiple of 90 degrees: rotate it in Blockbench or by hand')
    return {0: (1, 0), 90: (0, 1), 180: (-1, 0), 270: (0, -1)}[int(angle) % 360]


def matmul(a, b):
    return tuple(tuple(sum(a[r][k] * b[k][c] for k in range(3)) for c in range(3)) for r in range(3))


def apply(m, v):
    return tuple(sum(m[r][k] * v[k] for k in range(3)) for r in range(3))


def euler_matrix(rotation):
    """26.1's EulerXYZRotation: Matrix4f.rotationZYX(z, y, x) = Rz * Ry * Rx, JOML's right-handed rotations."""
    cx, sx = quarter_turn(rotation.get('x', 0))
    cy, sy = quarter_turn(rotation.get('y', 0))
    cz, sz = quarter_turn(rotation.get('z', 0))
    rx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    ry = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    rz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    return matmul(rz, matmul(ry, rx))


def rotate(m, origin, point):
    """FaceBakery's element rotation: M * (point - origin) + origin."""
    return tuple(o + d for o, d in zip(origin, apply(m, tuple(p - o for p, o in zip(point, origin)))))


def corner(face, frm, to, i):
    """Corner i of a face, picked from "from" and "to" as FaceInfo does."""
    return tuple(to[k] if FACE_CORNERS[face][i][k] else frm[k] for k in range(3))


def texture_point(uv, rotation, i):
    """The texture point corner i shows (26.1 CuboidFace#getU/getV, 1.21.1 BlockFaceUV#getU/getV)."""
    s = (i + int(rotation) // 90) % 4
    return (uv[0] if s in (0, 1) else uv[2], uv[1] if s in (0, 3) else uv[3])


def direction_of(normal):
    return next(name for name, n in DIRECTIONS.items() if n == tuple(int(c) for c in normal))


def face_extras(face):
    """Everything a face carries besides its UV mapping (texture, cullface, tintindex...): must not change."""
    return tuple((k, json.dumps(v, default=str)) for k, v in face.items() if k not in ('uv', 'rotation'))


def bake(element, rotated):
    """Every face of an element as the game bakes it: {direction it faces: (extras, frozenset of (corner, point))}.

    rotated=True applies the element's Euler rotation as 26.1 does; otherwise the element must have no rotation, or a
    0-degree one (1.21.1's shape of the rewritten elements). Returns None for rotations neither covers (left as
    they are, compared as text instead)."""
    rotation = element.get('rotation')
    m, origin = IDENTITY, (0, 0, 0)
    if rotation is not None:
        if is_euler(rotation) and rotated:
            m, origin = euler_matrix(rotation), tuple(rotation['origin'])
        elif is_euler(rotation) or Decimal(rotation.get('angle', 0)) != 0:
            return None
    frm, to = tuple(element['from']), tuple(element['to'])
    faces = {}
    for name, face in element['faces'].items():
        quad = frozenset((rotate(m, origin, corner(name, frm, to, i)),
                          texture_point(face['uv'], face.get('rotation', 0), i)) for i in range(4))
        faces[direction_of(apply(m, DIRECTIONS[name]))] = (face_extras(face), quad)
    return faces


def convert_element(element, where):
    """The element as an unrotated box with the same faces, or ConversionError."""
    rotation = element['rotation']
    m, origin = euler_matrix(rotation), tuple(rotation['origin'])
    frm, to = tuple(element['from']), tuple(element['to'])
    if any(f > t for f, t in zip(frm, to)):
        raise ConversionError(f'{where}: "from" is above "to" on some axis (an inside-out box)')
    corners = [rotate(m, origin, (frm[0] if a else to[0], frm[1] if b else to[1], frm[2] if c else to[2]))
               for a in (0, 1) for b in (0, 1) for c in (0, 1)]
    new_from = tuple(min(c[k] for c in corners) for k in range(3))
    new_to = tuple(max(c[k] for c in corners) for k in range(3))

    new_faces = {}
    for name, face in element['faces'].items():
        if 'uv' not in face:
            raise ConversionError(f'{where}, face {name}: no "uv" (automatic UVs are not converted)')
        target = frozenset((rotate(m, origin, corner(name, frm, to, i)),
                            texture_point(face['uv'], face.get('rotation', 0), i)) for i in range(4))
        new_name = direction_of(apply(m, DIRECTIONS[name]))
        fits = [r for r in FACE_ROTATIONS
                if frozenset((corner(new_name, new_from, new_to, i), texture_point(face['uv'], r, i))
                             for i in range(4)) == target]
        if not fits:
            raise ConversionError(f'{where}, face {name}: no face rotation reproduces its texture')
        new_face = {'uv': face['uv']}
        if fits[0]:
            new_face['rotation'] = Decimal(fits[0])
        new_face.update((k, v) for k, v in face.items() if k not in ('uv', 'rotation'))
        new_faces[new_name] = new_face

    converted = {}
    for key, value in element.items():
        if key == 'from':
            converted['from'] = new_from
        elif key == 'to':
            converted['to'] = new_to
        elif key == 'faces':
            converted['faces'] = {n: new_faces[n] for n in FACE_ORDER if n in new_faces}
        elif key != 'rotation':
            converted[key] = value
    return converted


def number(value):
    """A number as Blockbench writes it: 16, 0.5, -1.25 (no exponent, no trailing zeros, no -0)."""
    value = Decimal(value)
    return '0' if value == 0 else format(value.normalize(), 'f')


def value_text(value):
    if isinstance(value, bool):
        return 'true' if value else 'false'
    if isinstance(value, (Decimal, int)):
        return number(value)
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, (list, tuple)):
        return '[' + ', '.join(value_text(v) for v in value) + ']'
    return '{' + ', '.join(f'{json.dumps(k)}: {value_text(v)}' for k, v in value.items()) + '}'


def element_lines(element, comma):
    """An element in Blockbench's layout: two tabs, one key per line, one line per face."""
    lines = ['\t\t{']
    keys = list(element)
    for n, key in enumerate(keys):
        end = ',' if n < len(keys) - 1 else ''
        if key == 'faces':
            lines.append('\t\t\t"faces": {')
            names = list(element['faces'])
            for j, name in enumerate(names):
                lines.append(f'\t\t\t\t{json.dumps(name)}: {value_text(element["faces"][name])}'
                             + (',' if j < len(names) - 1 else ''))
            lines.append('\t\t\t}' + end)
        else:
            lines.append(f'\t\t\t{json.dumps(key)}: {value_text(element[key])}{end}')
    lines.append('\t\t}' + (',' if comma else ''))
    return lines


def element_spans(lines, elements):
    """Line spans [start, end] of each element in the exported text, checked against the parsed elements.

    Only the "elements" array is scanned: Blockbench's "groups" array uses the same two-tab braces."""
    try:
        first = lines.index('\t"elements": [') + 1
        last = next(i for i in range(first, len(lines)) if lines[i] in ('\t],', '\t]'))
    except (ValueError, StopIteration):
        raise ConversionError('no "elements" array in Blockbench\'s layout') from None
    spans, start = [], None
    for i in range(first, last):
        line = lines[i]
        if line == '\t\t{':
            start = i
        elif line in ('\t\t}', '\t\t},') and start is not None:
            spans.append((start, i))
            start = None
    if len(spans) != len(elements):
        raise ConversionError(f'{len(spans)} element blocks for {len(elements)} elements: not a Blockbench layout')
    for (s, e), element in zip(spans, elements):
        if load('\n'.join(lines[s:e + 1]).rstrip(',')) != element:
            raise ConversionError(f'element block at line {s + 1} does not match the parsed model')
    return spans


def check_1_21_1(model, name):
    """The limits 1.21.1's parser enforces (it refuses the whole model when one element breaks them)."""
    for n, element in enumerate(model.get('elements', [])):
        where = f'{name}, element {n}'
        for key in ('from', 'to'):
            if any(c < COORD_MIN or c > COORD_MAX for c in element[key]):
                raise ConversionError(f'{where}: "{key}" outside [-16, 32]')
        rotation = element.get('rotation')
        if rotation is not None:
            if rotation.get('axis') not in ('x', 'y', 'z') or 'origin' not in rotation:
                raise ConversionError(f'{where}: rotation needs "axis" and "origin" in 1.21.1')
            if Decimal(rotation.get('angle', 'NaN')) not in LEGACY_ANGLES:
                raise ConversionError(f'{where}: 1.21.1 only takes angles 0, +-22.5 and +-45')
        for face_name, face in element['faces'].items():
            if face_name not in DIRECTIONS or int(face.get('rotation', 0)) not in FACE_ROTATIONS:
                raise ConversionError(f'{where}, face {face_name}: not a 1.21.1 face')
            if 'uv' in face and len(face['uv']) != 4:
                raise ConversionError(f'{where}, face {face_name}: "uv" needs 4 numbers')


def convert_file(path):
    """The verified new content of a model (bytes), or None when it needs no change."""
    raw = path.read_bytes().decode('utf-8')
    eol = '\r\n' if '\r\n' in raw else '\n'
    text = raw.replace('\r\n', '\n')
    model = load(text)
    elements = model.get('elements', [])
    todo = [n for n, e in enumerate(elements) if is_euler(e.get('rotation'))]
    if not todo:
        check_1_21_1(model, path.name)
        print(f'{path.name}: nothing to convert, 1.21.1-valid')
        return None

    lines = text.split('\n')
    spans = element_spans(lines, elements)
    out = lines[:]
    for n in reversed(todo):  # from the end, so earlier spans keep their line numbers
        s, e = spans[n]
        converted = convert_element(elements[n], f'{path.name}, element {n}')
        out[s:e + 1] = element_lines(converted, lines[e].endswith(','))
    new_text = '\n'.join(out)

    # Proof: parse what would be written and compare it with the original, as each game bakes it.
    new_model = load(new_text)
    if {k: v for k, v in new_model.items() if k != 'elements'} != {k: v for k, v in model.items() if k != 'elements'}:
        raise ConversionError(f'{path.name}: a key other than the elements changed')
    new_elements = new_model['elements']
    if len(new_elements) != len(elements):
        raise ConversionError(f'{path.name}: the element count changed')
    turns = {}
    for n, (old, new) in enumerate(zip(elements, new_elements)):
        if n in todo:
            before, after = bake(old, rotated=True), bake(new, rotated=False)
            if before is None or after is None or before != after:
                raise ConversionError(f'{path.name}, element {n}: the rewritten faces differ from the original')
            for face in new['faces'].values():
                turns[int(face.get('rotation', 0))] = turns.get(int(face.get('rotation', 0)), 0) + 1
        elif old != new:
            raise ConversionError(f'{path.name}, element {n}: an element that needed no change changed')
    check_1_21_1(new_model, path.name)

    summary = ', '.join(f'{count} x {turn}' for turn, count in sorted(turns.items()))
    print(f'{path.name}: {len(todo)} of {len(elements)} elements rewritten, verified (face rotations: {summary})')
    return new_text.replace('\n', eol).encode('utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    parser.add_argument('--check', action='store_true', help='verify and report only, write nothing')
    parser.add_argument('files', nargs='*', type=Path, help='models to convert (default: every block model)')
    args = parser.parse_args()
    files = args.files or sorted(MODEL_DIR.glob('*.json'))
    if not files:
        sys.exit(f'no models found (run from the repository root; looked in {MODEL_DIR})')
    try:  # every file is converted and verified before any is written: all or nothing
        results = {path: convert_file(path) for path in files}
    except ConversionError as error:
        sys.exit(f'not converted, nothing written: {error}')
    changed = [path for path, content in results.items() if content is not None]
    if not args.check:
        for path in changed:
            path.write_bytes(results[path])
    verb = 'would rewrite' if args.check else 'rewrote'
    print(f'{verb} {len(changed)} of {len(files)} files')


if __name__ == '__main__':
    main()
