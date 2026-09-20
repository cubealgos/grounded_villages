#!/usr/bin/env python3
"""Render docs/modrinth/icon.png: the village bell on the cubealgos navy badge Create-family
add-ons share (GV-23).

Grounded Villages adds no block or item of its own (00-context.md: placement only, no new
building) -- the icon's subject is vanilla's own village bell, `minecraft:bell`, read straight
out of the Minecraft client jar in the Gradle cache and never vendored into this repo. The jar is
found by globbing
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/*.jar`
first, then any `~/.gradle/caches/fabric-loom/26.2/**/*.jar` that contains the bell's entity
texture; pass --jar PATH to use a specific jar instead.

The bell's own block models (`assets/minecraft/models/block/bell_floor.json` and its
between-walls/ceiling/wall siblings) are checked first and turn out to hold only the wooden
post-and-bar frame the bell hangs from -- the bell body itself is entity-rendered at runtime
(`BellRenderer`/`BellModel`, never baked into a block or item model JSON; the item texture
`textures/item/bell.png` is a flat, pre-baked 2D icon, not a projectable model). So the bell body
is reconstructed by hand as the two cuboids `BellModel.createBodyLayer()` actually builds
(decompiled from `net/minecraft/client/model/object/bell/BellModel.class` in the 26.2 jar):

    bell_body: pivot (8, 12, 8), box from (-3, -6, -3) size (6, 7, 6) -> absolute (5,6,5)-(11,13,11)
    bell_base: pivot (0, 0, 0) (bell_body's pivot + its own (-8, -12, -8) offset),
               box from (4, 4, 4) size (8, 2, 8) -> absolute (4,4,4)-(12,6,12)

textured from `assets/minecraft/textures/entity/bell/bell_body.png` (32x32), with each face's UV
rectangle computed by Minecraft's own box-UV layout (decompiled from
`ModelPart$Cube`'s constructor: row 1 holds a blank dz-wide gutter then the down and up faces
side by side, each dx wide and dz tall; row 2 holds west/north/east/south, each dy tall) --
the same "box UV" every `CubeListBuilder.addBox` call produces, verified against the actual
texture crop before use. This gives the exact geometry and texture `BellRenderer` draws, composed
here as an ordinary block-model element list and run through the same 3D projection the sibling
repos use, rather than a spec guess.

A second candidate sits the bell on a small slab of vanilla's dirt path (`dirt_path_top`/
`dirt_path_side`), standing in for "dry ground" -- the mod's whole subject
(`docs/spec/domains/site.md`) -- without inventing a new textured block. Both are rendered at the
siblings' isometric tilt (create_metered_motor's `[30, 315, -45]`, scale 0.625); the bell alone is
also rendered at vanilla's own default GUI tilt (`[30, 225, 0]`) as a second angle to compare.

Ports the projection and badge code inline from the heimathafen prototypes
(standards/marketing/modrinth/block-model-render.py and .../navy-badge.py), the way
create_metered_motor's tools/icon.py does, so this repo never imports from heimathafen at build
time. Requires Pillow.

Usage:
    python3 tools/icon.py                        # writes docs/modrinth/icon.png
    python3 tools/icon.py --sheet PATH            # also writes a candidates contact sheet
    python3 tools/icon.py --jar PATH_TO_CLIENT_JAR
"""
from __future__ import annotations

import argparse
import math
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "modrinth" / "icon.png"

GRADLE_CACHE = Path.home() / ".gradle" / "caches" / "fabric-loom"
PRIMARY_GLOB = GRADLE_CACHE / "minecraftMaven" / "net" / "minecraft" / "minecraft-merged-deobf" / "26.2"
FALLBACK_ROOT = GRADLE_CACHE / "26.2"

BELL_BODY_TEXTURE = "assets/minecraft/textures/entity/bell/bell_body.png"
DIRT_PATH_TOP = "assets/minecraft/textures/block/dirt_path_top.png"
DIRT_PATH_SIDE = "assets/minecraft/textures/block/dirt_path_side.png"

SIBLINGS_TILT = {"rotation": [30, 315, -45], "scale": [0.625, 0.625, 0.625]}
VANILLA_TILT = {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]}


# ============================================================== bell geometry, decompiled
# BellModel.createBodyLayer() (net/minecraft/client/model/object/bell/BellModel.class, 26.2):
#   bell_body: texOffs(0, 0),  addBox(-3,-6,-3, 6,7,6), offset(8, 12, 8)
#   bell_base: texOffs(0, 13), addBox(4,4,4, 8,2,8),    offset(-8,-12,-8) (child of bell_body)
# Absolute block-space boxes (bell_base's offset is relative to bell_body's own pivot):
#   bell_body: (5,6,5) -> (11,13,11), size (6,7,6), texOffs (0,0)
#   bell_base: (4,4,4) -> (12,6,12),  size (8,2,8), texOffs (0,13)
def _box_uv(tu: float, tv: float, dx: float, dy: float, dz: float) -> dict:
    """Minecraft's own box-UV layout, decompiled from ModelPart$Cube's constructor: a blank
    dz-wide gutter then down/up (each dx x dz) in row 1, west/north/east/south (each dy tall)
    in row 2. "up" is vertically flipped relative to "down" (v1 > v2), matching the bytecode."""
    return {
        "down": [tu + dz, tv, tu + dz + dx, tv + dz],
        "up": [tu + dz + dx, tv + dz, tu + dz + 2 * dx, tv],
        "west": [tu, tv + dz, tu + dz, tv + dz + dy],
        "north": [tu + dz, tv + dz, tu + dz + dx, tv + dz + dy],
        "east": [tu + dz + dx, tv + dz, tu + 2 * dz + dx, tv + dz + dy],
        "south": [tu + 2 * dz + dx, tv + dz, tu + 2 * dz + 2 * dx, tv + dz + dy],
    }


def _cuboid_element(frm, to, tex_key: str, uv: dict) -> dict:
    return {
        "from": list(frm),
        "to": list(to),
        "faces": {face: {"uv": uv[face], "texture": f"#{tex_key}"} for face in uv},
    }


def bell_elements() -> list[dict]:
    body_uv = _box_uv(0, 0, 6, 7, 6)
    base_uv = _box_uv(0, 13, 8, 2, 8)
    return [
        _cuboid_element((5, 6, 5), (11, 13, 11), "body", body_uv),
        _cuboid_element((4, 4, 4), (12, 6, 12), "body", base_uv),
    ]


def ground_slab_element() -> dict:
    """A small dirt-path slab under the bell, standing in for "dry ground" -- this mod's whole
    subject -- without inventing a block that doesn't exist. Height 0-4 so it meets the bell's
    base (which starts at y=4) with no gap and no clipping."""
    uv = {
        "down": [0, 0, 16, 16],
        "up": [0, 0, 16, 16],
        "west": [0, 12, 16, 16],
        "north": [0, 12, 16, 16],
        "east": [0, 12, 16, 16],
        "south": [0, 12, 16, 16],
    }
    element = _cuboid_element((1, 0, 1), (15, 4, 15), "ground_side", uv)
    element["faces"]["up"]["texture"] = "#ground_top"
    element["faces"]["down"]["texture"] = "#ground_top"
    return element


# ============================================================== 3D projection
# Ported from heimathafen standards/marketing/modrinth/block-model-render.py.

RENDER_SIZE = 512
RENDER_SUPERSAMPLE = 4
RENDER_CANVAS = RENDER_SIZE * RENDER_SUPERSAMPLE

FACE_VERTS = {
    "down":  [(0, 0, 0), (0, 0, 1), (1, 0, 1), (1, 0, 0)],
    "up":    [(0, 1, 1), (0, 1, 0), (1, 1, 0), (1, 1, 1)],
    "north": [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    "west":  [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    "east":  [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}
FACE_NORMAL = {
    "down": (0, -1, 0), "up": (0, 1, 0),
    "north": (0, 0, -1), "south": (0, 0, 1),
    "west": (-1, 0, 0), "east": (1, 0, 0),
}
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def rot_axis(p, axis, deg):
    ang = math.radians(deg)
    c, s = math.cos(ang), math.sin(ang)
    x, y, z = p
    if axis == "x":
        return (x, y * c - z * s, y * s + z * c)
    if axis == "y":
        return (x * c + z * s, y, -x * s + z * c)
    return (x * c - y * s, x * s + y * c, z)


def rotate_about(p, origin, axis, deg):
    rel = (p[0] - origin[0], p[1] - origin[1], p[2] - origin[2])
    r = rot_axis(rel, axis, deg)
    return (r[0] + origin[0], r[1] + origin[1], r[2] + origin[2])


def display_transform(p, pivot, rx, ry, rz):
    rel = (p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2])
    rel = rot_axis(rel, "x", rx)
    rel = rot_axis(rel, "y", ry)
    rel = rot_axis(rel, "z", rz)
    return rel


def affine_from_points(src, dst):
    """3-point affine solve: dst = A*src + t. Returns forward (a,b,c,d,e,f)."""
    (x0, y0), (x1, y1), (x2, y2) = src
    (u0, v0), (u1, v1), (u2, v2) = dst
    mat = [[x0, y0, 1], [x1, y1, 1], [x2, y2, 1]]
    det = (mat[0][0] * (mat[1][1] * mat[2][2] - mat[1][2] * mat[2][1])
           - mat[0][1] * (mat[1][0] * mat[2][2] - mat[1][2] * mat[2][0])
           + mat[0][2] * (mat[1][0] * mat[2][1] - mat[1][1] * mat[2][0]))
    if abs(det) < 1e-9:
        return None

    def solve(vals):
        res = []
        for col in range(3):
            m2 = [row[:] for row in mat]
            for r in range(3):
                m2[r][col] = vals[r]
            d = (m2[0][0] * (m2[1][1] * m2[2][2] - m2[1][2] * m2[2][1])
                 - m2[0][1] * (m2[1][0] * m2[2][2] - m2[1][2] * m2[2][0])
                 + m2[0][2] * (m2[1][0] * m2[2][1] - m2[1][1] * m2[2][0]))
            res.append(d / det)
        return res

    a, b, c = solve([u0, u1, u2])
    d, e, f = solve([v0, v1, v2])
    return (a, b, c, d, e, f)


def invert_affine(coef):
    a, b, c, d, e, f = coef
    det = a * e - b * d
    if abs(det) < 1e-9:
        return None
    ia = e / det
    ib = -b / det
    ic = -(ia * c + ib * f)
    id_ = -d / det
    ie = a / det
    if_ = -(id_ * c + ie * f)
    return (ia, ib, ic, id_, ie, if_)


def build_faces(model, textures, pivot, gui):
    """Returns list of (depth, canvas_quad[4], texture_img, uv_patch_quad[4], shade)."""
    faces = []
    rx, ry, rz = gui["rotation"]
    for elem in model["elements"]:
        frm, to = elem["from"], elem["to"]
        erot = elem.get("rotation")
        corners = {}
        for bx in (0, 1):
            for by in (0, 1):
                for bz in (0, 1):
                    p = (
                        frm[0] if bx == 0 else to[0],
                        frm[1] if by == 0 else to[1],
                        frm[2] if bz == 0 else to[2],
                    )
                    if erot:
                        p = rotate_about(p, erot["origin"], erot["axis"], erot["angle"])
                    corners[(bx, by, bz)] = p
        for face_name, face in elem.get("faces", {}).items():
            verts_frac = FACE_VERTS[face_name]
            verts3d = [corners[v] for v in verts_frac]

            normal = FACE_NORMAL[face_name]
            if erot:
                normal = rot_axis(normal, erot["axis"], erot["angle"])
            cam_normal = rot_axis(rot_axis(rot_axis(normal, "x", rx), "y", ry), "z", rz)
            if cam_normal[2] <= 1e-4:
                continue  # backface culled

            cam_pts = [display_transform(p, pivot, rx, ry, rz) for p in verts3d]
            depth = sum(p[2] for p in cam_pts) / 4.0
            canvas_quad = [(p[0], -p[1]) for p in cam_pts]

            tex_key = face["texture"].lstrip("#")
            tex_img = textures[tex_key]
            u1, v1, u2, v2 = face["uv"]
            flip_x = u1 > u2
            flip_y = v1 > v2
            lo = (min(u1, u2), min(v1, v2))
            hi = (max(u1, u2), max(v1, v2))
            patch = tex_img.crop((round(lo[0]), round(lo[1]), round(hi[0]), round(hi[1])))
            if patch.width == 0 or patch.height == 0:
                continue
            if flip_x:
                patch = patch.transpose(Image.FLIP_LEFT_RIGHT)
            if flip_y:
                patch = patch.transpose(Image.FLIP_TOP_BOTTOM)
            pw, ph = patch.size
            default_patch_quad = [(0, 0), (0, ph), (pw, ph), (pw, 0)]
            rotation = face.get("rotation", 0)
            shift = (rotation // 90) % 4
            patch_quad = [default_patch_quad[(i + shift) % 4] for i in range(4)]

            faces.append((depth, canvas_quad, patch, patch_quad, SHADE[face_name]))
    faces.sort(key=lambda f: f[0])  # far to near
    return faces


def fit_scale(faces, canvas, margin=0.88):
    xs, ys = [], []
    for _, quad, *_ in faces:
        for x, y in quad:
            xs.append(x)
            ys.append(y)
    w = max(xs) - min(xs)
    h = max(ys) - min(ys)
    cx = (max(xs) + min(xs)) / 2
    cy = (max(ys) + min(ys)) / 2
    scale = (canvas * margin) / max(w, h)
    return scale, cx, cy


def render_model(model, textures, gui):
    pivot = (8.0, 8.0, 8.0)
    faces = build_faces(model, textures, pivot, gui)
    scale, cx, cy = fit_scale(faces, RENDER_CANVAS)

    canvas = Image.new("RGBA", (RENDER_CANVAS, RENDER_CANVAS), (0, 0, 0, 0))
    for depth, quad, patch, patch_quad, shade in faces:
        dst = [((x - cx) * scale + RENDER_CANVAS / 2, (y - cy) * scale + RENDER_CANVAS / 2)
               for x, y in quad]
        patch = patch.convert("RGBA")
        if shade != 1.0:
            r, g, b, a = patch.split()
            r = r.point(lambda v: int(v * shade))
            g = g.point(lambda v: int(v * shade))
            b = b.point(lambda v: int(v * shade))
            patch = Image.merge("RGBA", (r, g, b, a))

        fwd = affine_from_points(patch_quad[:3], dst[:3])
        if fwd is None:
            continue
        inv = invert_affine(fwd)
        if inv is None:
            continue
        layer = patch.transform((RENDER_CANVAS, RENDER_CANVAS), Image.AFFINE, inv,
                                 resample=Image.NEAREST, fillcolor=(0, 0, 0, 0))
        canvas.alpha_composite(layer)

    return canvas.resize((RENDER_SIZE, RENDER_SIZE), Image.LANCZOS)


# ============================================================== navy badge
# Ported from heimathafen standards/marketing/modrinth/navy-badge.py.

BADGE_SIZE = 512
BADGE_CENTRE = BADGE_SIZE // 2
BADGE_SUPERSAMPLE = 4
RIM = (255, 255, 255, 255)
BAND = (232, 236, 244, 255)
RING = (9, 12, 27, 255)
BLUEPRINT = (13, 18, 38, 255)
GRID = (52, 76, 128, 255)
OUTLINE = (255, 255, 255, 235)
SHADOW = (20, 50, 90, 130)
FIT_BOX = 320  # smooth mode, already-rendered subject, as the siblings use


def badge() -> Image.Image:
    big = BADGE_SIZE * BADGE_SUPERSAMPLE
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for radius, colour in ((256, RIM), (250, BAND), (238, RING), (200, BLUEPRINT)):
        r = radius * BADGE_SUPERSAMPLE
        c = BADGE_CENTRE * BADGE_SUPERSAMPLE
        draw.ellipse((c - r, c - r, c + r, c + r), fill=colour)
    img = img.resize((BADGE_SIZE, BADGE_SIZE), Image.LANCZOS)

    grid = Image.new("RGBA", (BADGE_SIZE, BADGE_SIZE), (0, 0, 0, 0))
    g = ImageDraw.Draw(grid)
    for k in range(-4, 5):
        p = BADGE_CENTRE + k * 48
        g.line((p, 0, p, BADGE_SIZE), fill=GRID, width=3)
        g.line((0, p, BADGE_SIZE, p), fill=GRID, width=3)
    glow = Image.new("RGBA", (BADGE_SIZE, BADGE_SIZE), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse(
        (BADGE_CENTRE - 120, BADGE_CENTRE - 120, BADGE_CENTRE + 120, BADGE_CENTRE + 120),
        fill=(34, 48, 92, 150))
    glow = glow.filter(ImageFilter.GaussianBlur(50))
    inner = Image.alpha_composite(glow, grid)
    mask = Image.new("L", (BADGE_SIZE, BADGE_SIZE), 0)
    ImageDraw.Draw(mask).ellipse(
        (BADGE_CENTRE - 238, BADGE_CENTRE - 238, BADGE_CENTRE + 238, BADGE_CENTRE + 238),
        fill=255)
    clipped = Image.new("RGBA", (BADGE_SIZE, BADGE_SIZE), (0, 0, 0, 0))
    clipped.paste(inner, (0, 0), mask)
    return Image.alpha_composite(img, clipped)


def compose(base: Image.Image, sprite: Image.Image, box: int = FIT_BOX) -> Image.Image:
    """"smooth" mode: sprite is already-rendered/anti-aliased art (the bell's projection), so it
    is LANCZOS-scaled to fit, not zoomed like a raw pixel-art texture."""
    w, h = sprite.size
    longest = max(w, h)
    factor = box / longest
    size = (round(w * factor), round(h * factor))
    scale = size[0] / w
    sprite = sprite.resize(size, Image.LANCZOS)
    alpha = sprite.getchannel("A")
    x = BADGE_CENTRE - size[0] // 2
    y = BADGE_CENTRE - size[1] // 2
    step = max(1, round(scale))
    grown = Image.new("L", (BADGE_SIZE, BADGE_SIZE), 0)
    for dx in (-step, 0, step):
        for dy in (-step, 0, step):
            grown.paste(alpha, (x + dx, y + dy), alpha)
    shadow = Image.new("RGBA", (BADGE_SIZE, BADGE_SIZE), (0, 0, 0, 0))
    shadow.paste(SHADOW, (0, 0), grown.transform(grown.size, Image.AFFINE, (1, 0, -14, 0, 1, -14)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(10))
    outline = Image.new("RGBA", (BADGE_SIZE, BADGE_SIZE), (0, 0, 0, 0))
    outline.paste(OUTLINE, (0, 0), grown)
    img = Image.alpha_composite(base, shadow)
    img = Image.alpha_composite(img, outline)
    img.alpha_composite(sprite, (x, y))
    return img


# ============================================================== jar / texture resolution

def jar_has_entry(jar: Path, entry: str) -> bool:
    try:
        with zipfile.ZipFile(jar) as zf:
            return entry in zf.namelist()
    except (OSError, zipfile.BadZipFile):
        return False


def find_jar(explicit: Path | None) -> Path:
    if explicit is not None:
        if not explicit.exists():
            raise SystemExit(f"icon: --jar {explicit} does not exist")
        return explicit
    for candidate in sorted(PRIMARY_GLOB.glob("*.jar")):
        if jar_has_entry(candidate, BELL_BODY_TEXTURE):
            return candidate
    for candidate in sorted(FALLBACK_ROOT.glob("**/*.jar")):
        if jar_has_entry(candidate, BELL_BODY_TEXTURE):
            return candidate
    raise SystemExit(
        "icon: no Minecraft client jar with "
        f"{BELL_BODY_TEXTURE} found under {PRIMARY_GLOB} or {FALLBACK_ROOT}; "
        "run a Gradle build to populate the cache, or pass --jar PATH"
    )


def load_texture(jar: Path, entry: str) -> Image.Image:
    with zipfile.ZipFile(jar) as zf:
        with zf.open(entry) as fh:
            return Image.open(fh).convert("RGBA").copy()


# ============================================================== candidates

def render_bell(textures: dict, gui: dict) -> Image.Image:
    model = {"elements": bell_elements()}
    return render_model(model, textures, gui)


def render_bell_on_slab(textures: dict, gui: dict) -> Image.Image:
    model = {"elements": bell_elements() + [ground_slab_element()]}
    return render_model(model, textures, gui)


def build_candidates(jar: Path) -> dict:
    bell_tex = load_texture(jar, BELL_BODY_TEXTURE)
    ground_top = load_texture(jar, DIRT_PATH_TOP)
    ground_side = load_texture(jar, DIRT_PATH_SIDE)
    textures = {"body": bell_tex, "ground_top": ground_top, "ground_side": ground_side}

    return {
        "bell-siblings-tilt": render_bell(textures, SIBLINGS_TILT),
        "bell-vanilla-tilt": render_bell(textures, VANILLA_TILT),
        "bell-on-slab": render_bell_on_slab(textures, SIBLINGS_TILT),
    }


def _org_root() -> Path:
    """The cubealgos org directory that holds every sibling repo, resolved via git rather than
    a fixed parent count -- this repo is usually checked out into a ticket worktree
    (.worktrees/<slug>/), so ROOT.parent is .worktrees, not the org directory."""
    try:
        import subprocess
        common_dir = subprocess.run(
            ["git", "rev-parse", "--git-common-dir"], cwd=ROOT,
            capture_output=True, text=True, check=True,
        ).stdout.strip()
        repo_root = (ROOT / common_dir).resolve().parent  # .../grounded_villages/.git -> grounded_villages
        return repo_root.parent  # .../cubealgos
    except Exception:
        return ROOT.parent


SIBLING_ICONS = [
    _org_root() / "create_brass_compass" / "docs" / "modrinth" / "icon.png",
    _org_root() / "create_metered_motor" / "docs" / "modrinth" / "icon.png",
    _org_root() / "create_villager_customers" / "docs" / "modrinth" / "icon.png",
    _org_root() / "villager_voices" / "docs" / "modrinth" / "icon.png",
]


def write_sheet(candidates: dict, path: Path) -> None:
    """A contact sheet: each candidate badge (and each existing sibling icon, for consistency)
    shown at 512px and at 64px, so the icon can be judged the way Modrinth actually shows it."""
    labels = list(candidates.keys())
    badges = [compose(badge(), candidates[k]) for k in labels]
    sibling_labels = []
    for sib in SIBLING_ICONS:
        if sib.exists():
            badges.append(Image.open(sib).convert("RGBA"))
            sibling_labels.append(sib.parent.parent.parent.name)
    labels = labels + sibling_labels

    cell = 512 + 40
    small = 64
    cols = len(badges)
    label_h = 28
    sheet = Image.new("RGBA", (cell * cols, 512 + small + label_h * 2 + 40), (245, 246, 248, 255))
    draw = ImageDraw.Draw(sheet)
    for i, b in enumerate(badges):
        x = i * cell + 20
        sheet.alpha_composite(b, (x, label_h))
        small_im = b.resize((small, small), Image.LANCZOS)
        sheet.alpha_composite(small_im, (x + (512 - small) // 2, 512 + label_h + label_h))
        draw.text((x, 4), labels[i][:22], fill=(20, 20, 30, 255))
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)
    print(f"wrote {path} ({path.stat().st_size} bytes)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jar", type=Path, default=None, help="Minecraft client jar to read the bell's assets from")
    parser.add_argument("--sheet", type=Path, default=None, help="also write a candidates contact sheet to this path")
    parser.add_argument("--pick", default="bell-siblings-tilt",
                         choices=["bell-siblings-tilt", "bell-vanilla-tilt", "bell-on-slab"],
                         help="which candidate to write as docs/modrinth/icon.png")
    args = parser.parse_args()

    jar = find_jar(args.jar)
    candidates = build_candidates(jar)

    if args.sheet:
        write_sheet(candidates, args.sheet)

    icon = compose(badge(), candidates[args.pick])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    icon.save(OUT, optimize=True)
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes) from {jar}, candidate {args.pick!r}")


if __name__ == "__main__":
    main()
