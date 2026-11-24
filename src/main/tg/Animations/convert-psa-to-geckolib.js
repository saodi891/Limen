const fs = require('fs');
const path = require('path');

const sourceDirectory = __dirname;
const rootDirectory = path.resolve(sourceDirectory, '../../../..');
const resourcesDirectory = path.join(rootDirectory, 'src/main/resources');
const geoPath = path.join(resourcesDirectory, 'assets/limen/geo/entity/tower_guard.geo.json');
const outputPath = path.join(resourcesDirectory, 'assets/limen/animations/entity/tower_guard.animation.json');

// Unreal (forward, right, up) to Bedrock local (left, up, back), matching this Geo's -Z facing.

function readPsa(file) {
  const data = fs.readFileSync(path.resolve(sourceDirectory, file));
  const chunks = new Map();
  let offset = 0;
  while (offset + 32 <= data.length) {
    const name = data.toString('ascii', offset, offset + 20).replace(/\0.*$/, '');
    const size = data.readInt32LE(offset + 24);
    const count = data.readInt32LE(offset + 28);
    const start = offset + 32;
    if (size < 0 || count < 0 || start + size * count > data.length) break;
    chunks.set(name, { start, size, count });
    offset = start + size * count;
  }

  const bonesChunk = chunks.get('BONENAMES');
  const infoChunk = chunks.get('ANIMINFO');
  const keysChunk = chunks.get('ANIMKEYS');
  if (!bonesChunk || !infoChunk || !keysChunk) throw new Error(`${file}: missing PSA chunks`);

  const bones = Array.from({ length: bonesChunk.count }, (_, index) => {
    const start = bonesChunk.start + index * bonesChunk.size;
    return {
      name: data.toString('ascii', start, start + 64).replace(/\0.*$/, ''),
      parent: data.readInt32LE(start + 72),
      bindRotation: normalize([0, 1, 2, 3].map(i => data.readFloatLE(start + 76 + i * 4))),
      bindPosition: [0, 1, 2].map(i => data.readFloatLE(start + 92 + i * 4)),
    };
  });
  const info = infoChunk.start;
  const frameCount = data.readInt32LE(info + 164);
  const fps = data.readFloatLE(info + 152);
  if (frameCount <= 0 || fps <= 0 || keysChunk.count !== frameCount * bones.length) {
    throw new Error(`${file}: invalid frame metadata (${frameCount} frames, ${bones.length} bones, ${keysChunk.count} keys)`);
  }
  const boneIndices = new Map(bones.map((bone, index) => [bone.name, index]));
  const key = (frame, boneIndex) => {
    const start = keysChunk.start + (frame * bones.length + boneIndex) * keysChunk.size;
    return {
      position: [0, 1, 2].map(i => data.readFloatLE(start + i * 4)),
      rotation: normalize([0, 1, 2, 3].map(i => data.readFloatLE(start + 12 + i * 4))),
    };
  };
  return { file, bones, boneIndices, frameCount, fps, key };
}

function normalize(q) {
  const length = Math.hypot(...q);
  if (!Number.isFinite(length) || length < 1e-8) throw new Error('Invalid zero-length quaternion');
  return q.map(value => value / length);
}

function multiply(a, b) {
  return [
    a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
    a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
    a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
    a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2],
  ];
}

function inverse(q) {
  return [-q[0], -q[1], -q[2], q[3]];
}

function rotate(q, v) {
  const p = [v[0], v[1], v[2], 0];
  const r = multiply(multiply(q, p), inverse(q));
  return r.slice(0, 3);
}

function convertRotation(q) {
  // This basis has positive handedness, so rotate the quaternion vector with the same basis.
  return normalize([-q[1], q[2], -q[0], q[3]]);
}

function globalPose(psa, frame) {
  const result = [];
  for (let index = 0; index < psa.bones.length; index++) {
    const bone = psa.bones[index];
    const key = frame == null ? null : psa.key(frame, index);
    const localRotation = key ? key.rotation : bone.bindRotation;
    const localPosition = key ? key.position : bone.bindPosition;
    if (bone.parent < 0) {
      result[index] = { rotation: localRotation, position: localPosition };
    } else {
      const parent = result[bone.parent];
      result[index] = {
        rotation: normalize(multiply(parent.rotation, localRotation)),
        position: add(parent.position, rotate(parent.rotation, localPosition)),
      };
    }
  }
  return result;
}

function add(a, b) {
  return a.map((value, i) => value + b[i]);
}

function subtract(a, b) {
  return a.map((value, i) => value - b[i]);
}

function dot(a, b) {
  return a.reduce((sum, value, i) => sum + value * b[i], 0);
}

function quaternionDelta(current, reference) {
  let result = normalize(multiply(inverse(reference), current));
  if (result[3] < 0) result = result.map(value => -value);
  return result;
}

function toEulerDegrees(q) {
  const [x, y, z, w] = q;
  const sinX = 2 * (w * x + y * z);
  const cosX = 1 - 2 * (x * x + y * y);
  const sinY = Math.max(-1, Math.min(1, 2 * (w * y - z * x)));
  const sinZ = 2 * (w * z + x * y);
  const cosZ = 1 - 2 * (y * y + z * z);
  return [Math.atan2(sinX, cosX), Math.asin(sinY), Math.atan2(sinZ, cosZ)]
    .map(value => Number((value * 180 / Math.PI).toFixed(3)));
}

function unwrapEuler(previous, current) {
  return current.map((value, index) => {
    while (value - previous[index] > 180) value -= 360;
    while (value - previous[index] < -180) value += 360;
    return value;
  });
}

const mapping = {
  guard: 'J_Root',
  waist: 'J_L_Hip',
  body: 'Body',
  head: 'Head',
  LeftArm: 'L_Arm',
  RightArm: 'R_Arm',
  LeftLeg: 'L_Leg',
  RightLeg: 'R_Leg',
  cloak: 'J_Cloth',
};

function getGeo() {
  const geo = JSON.parse(fs.readFileSync(geoPath, 'utf8'))['minecraft:geometry'][0];
  const bones = new Map(geo.bones.map(bone => [bone.name, bone]));
  for (const targetBone of Object.keys(mapping)) {
    if (!bones.has(targetBone)) throw new Error(`Geo model is missing target bone ${targetBone}`);
  }
  return bones;
}

function globalRotationDeltas(psa, referencePose, frame) {
  const pose = globalPose(psa, frame);
  return pose.map((transform, index) => normalize(multiply(
    transform.rotation,
    inverse(referencePose[index].rotation),
  )));
}

function makeAnimation(file, loop = false) {
  const psa = readPsa(file);
  const geoBones = getGeo();
  const referencePose = globalPose(psa, 0);
  const mapped = new Map(Object.entries(mapping).map(([targetName, sourceName]) => {
    const sourceIndex = psa.boneIndices.get(sourceName);
    if (sourceIndex == null) throw new Error(`${file}: missing mapped source bone ${sourceName}`);
    return [targetName, sourceIndex];
  }));
  const duration = psa.frameCount / psa.fps;
  const animation = { loop, animation_length: Number(duration.toFixed(4)), bones: {} };
  const maxRotationByBone = {};

  for (const [targetName, sourceIndex] of mapped) {
    const targetBone = geoBones.get(targetName);
    const targetParent = targetBone.parent;
    const targetParentSource = targetParent ? mapped.get(targetParent) : null;
    const rotationKeys = {};
    let previousEuler = [0, 0, 0];
    let maxRotation = 0;

    for (let frame = 0; frame < psa.frameCount; frame++) {
      const deltas = globalRotationDeltas(psa, referencePose, frame);
      const parentDelta = targetParentSource == null ? [0, 0, 0, 1] : deltas[targetParentSource];
      const localRotation = convertRotation(normalize(multiply(inverse(parentDelta), deltas[sourceIndex])));
      const euler = unwrapEuler(previousEuler, toEulerDegrees(localRotation));
      previousEuler = euler;
      maxRotation = Math.max(maxRotation, 2 * Math.acos(Math.min(1, Math.abs(localRotation[3]))) * 180 / Math.PI);
      const time = (frame / psa.fps).toFixed(4);
      rotationKeys[time] = { vector: euler.map(value => Number(value.toFixed(3))), lerp_mode: 'linear' };
    }

    if (loop) {
      const endTime = duration.toFixed(4);
      rotationKeys[endTime] = { ...rotationKeys['0.0000'] };
    } else {
      const endTime = duration.toFixed(4);
      const lastTime = ((psa.frameCount - 1) / psa.fps).toFixed(4);
      rotationKeys[endTime] = { ...rotationKeys[lastTime] };
    }

    animation.bones[targetName] = { rotation: rotationKeys };
    maxRotationByBone[targetName] = Number(maxRotation.toFixed(2));
  }

  return { animation, diagnostics: { file, frames: psa.frameCount, fps: psa.fps, maxRotationByBone } };
}

const clips = [
  ['animation.tower_guard.idle', '../IllagerRoyalGuard_Idle.psa', true],
  ['animation.tower_guard.walk', '../IllagerRoyalGuard_Walk.psa', true],
  ['animation.tower_guard.basic_attack', 'TowerGuard_BasicAttack.psa', false],
  ['animation.tower_guard.charge', 'TowerGuard_ChargeAttack_Charging.psa', true],
  ['animation.tower_guard.shield_break', 'TowerGuard_ChargeAttack_ShieldBroken.psa', false],
];

const animations = {};
const diagnostics = [];
for (const [name, file, loop] of clips) {
  const result = makeAnimation(file, loop);
  animations[name] = result.animation;
  diagnostics.push(result.diagnostics);
}

const output = { format_version: '1.8.0', animations };
fs.writeFileSync(outputPath, `${JSON.stringify(output, null, 2)}\n`);
JSON.parse(fs.readFileSync(outputPath, 'utf8'));

for (const result of diagnostics) {
  const rotations = Object.values(result.maxRotationByBone);
  console.log(`${result.file}: ${result.frames} frames @ ${result.fps.toFixed(2)} fps; `
    + `max mapped rotation ${Math.max(...rotations).toFixed(1)} deg; `
    + `tracks=${Object.keys(result.maxRotationByBone).join(', ')}`);
}
console.log(`Wrote and parsed ${Object.keys(animations).length} animations: ${outputPath}`);
