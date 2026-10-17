"""Bakes blockstate rotations into block models, for game versions whose blockstates can't turn a model about z.

Minecraft 1.21.11 added "z" to blockstate variants. It turns the model's corners about the block's centre after "x"
and "y", each a clockwise quarter turn looking from the positive end of its axis. Before 1.21.11 a variant only has x
and y, which can't reach every orientation, so turn() writes the turned model as a model of its own: the same boxes
moved to where the rotation puts them, each face's texture turned to match.
"""

DIRECTIONS = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
DIRECTION_NAMES = {vector: name for name, vector in DIRECTIONS.items()}
AXIS_VECTORS = {"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}

# The corners of a face in the order the game gives them texture coordinates (FaceInfo).
CORNERS = {
    "down": [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    "north": [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    "west": [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    "east": [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}


def quarter_turns(x, y, z):
    """The rotation of a variant with these angles, as a function on vectors from the block's centre."""
    steps = [lambda v: (v[0], v[2], -v[1])] * (x // 90 % 4) + [lambda v: (-v[2], v[1], v[0])] * (y // 90 % 4) \
        + [lambda v: (v[1], -v[0], v[2])] * (z // 90 % 4)

    def turn(vector):
        for step in steps:
            vector = step(vector)
        return vector
    return turn


def default_uv(direction, start, end):
    """The texture coordinates the game gives a face that names none (BlockElement.uvsByFace)."""
    return {
        "down": [start[0], 16 - end[2], end[0], 16 - start[2]],
        "up": [start[0], start[2], end[0], end[2]],
        "north": [16 - end[0], 16 - end[1], 16 - start[0], 16 - start[1]],
        "south": [start[0], 16 - end[1], end[0], 16 - start[1]],
        "west": [start[2], 16 - end[1], end[2], 16 - start[1]],
        "east": [16 - end[2], 16 - end[1], 16 - start[2], 16 - start[1]],
    }[direction]


def face_points(direction, start, end, uv, rotation):
    """Each corner of a face with the texture coordinates the game puts there (BlockFaceUV.getU/getV)."""
    points = set()
    for i, corner in enumerate(CORNERS[direction]):
        position = tuple(round(end[a] if corner[a] else start[a], 4) for a in range(3))
        j = (i + rotation // 90) % 4
        points.add((position, (uv[2] if j in (2, 3) else uv[0], uv[3] if j in (1, 2) else uv[1])))
    return points


def turn_point(turn, point):
    turned = turn(tuple(c - 8 for c in point))
    return tuple(round(c + 8, 4) for c in turned)


def turn_element(element, turn):
    start, end = element["from"], element["to"]
    corners = [turn_point(turn, (end[0] if i & 1 else start[0], end[1] if i & 2 else start[1], end[2] if i & 4 else start[2])) for i in range(8)]
    new_start = [min(c[a] for c in corners) for a in range(3)]
    new_end = [max(c[a] for c in corners) for a in range(3)]
    turned = {k: v for k, v in element.items() if k not in ("from", "to", "faces", "rotation")}
    turned["from"], turned["to"] = new_start, new_end
    faces = {}
    for direction, face in element.get("faces", {}).items():
        uv = face.get("uv") or default_uv(direction, start, end)
        wanted = {(turn_point(turn, position), coords) for position, coords in face_points(direction, start, end, uv, face.get("rotation", 0))}
        new_direction = DIRECTION_NAMES[turn(DIRECTIONS[direction])]
        rotation = next(r for r in (0, 90, 180, 270) if face_points(new_direction, new_start, new_end, uv, r) == wanted)
        new_face = {k: v for k, v in face.items() if k not in ("uv", "rotation", "cullface")}
        new_face["uv"] = uv
        if rotation:
            new_face["rotation"] = rotation
        if "cullface" in face:
            new_face["cullface"] = DIRECTION_NAMES[turn(DIRECTIONS[face["cullface"]])]
        faces[new_direction] = new_face
    turned["faces"] = faces
    if "rotation" in element:
        # Turning the box after its own tilt is the same as tilting the turned box about the turned axis.
        tilt = dict(element["rotation"])
        axis = turn(AXIS_VECTORS[tilt["axis"]])
        tilt["axis"] = "xyz"[next(a for a in range(3) if axis[a])]
        if sum(axis) < 0:
            tilt["angle"] = -tilt["angle"]
        tilt["origin"] = list(turn_point(turn, tilt.get("origin", [8, 8, 8])))
        turned["rotation"] = tilt
    return turned


def turn(model, x, y, z):
    """A full model (no parent) turned the way a variant with these angles turns it."""
    rotate = quarter_turns(x, y, z)
    turned = {k: v for k, v in model.items() if k != "elements"}
    turned["elements"] = [turn_element(element, rotate) for element in model.get("elements", [])]
    return turned
