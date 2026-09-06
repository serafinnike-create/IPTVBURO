/**
 * Hero 3D — a wall of catalogue posters drifting in depth behind the headline.
 *
 * Loaded lazily and only where it can be afforded: the module is fetched after
 * first paint, and it refuses to start on reduced-motion, small screens, low
 * core counts, or when WebGL is unavailable. The static hero shot stays as the
 * baseline in every one of those cases, so nothing here is load-bearing.
 */
const ATLAS = 'assets/poster-atlas.webp';
const COLS = 6, ROWS = 3, TILES = COLS * ROWS;

export function supported() {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return false;
  if (window.innerWidth < 900) return false;
  if ((navigator.hardwareConcurrency || 2) < 4) return false;
  if (navigator.connection && navigator.connection.saveData) return false;
  try {
    const probe = document.createElement('canvas');
    return Boolean(probe.getContext('webgl2') || probe.getContext('webgl'));
  } catch (_) {
    return false;
  }
}

export async function start(mount) {
  const THREE = await import('./vendor/three.module.min.js');

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 100);
  camera.position.set(0, 0, 14);

  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' });
  renderer.setClearColor(0x000000, 0);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  mount.appendChild(renderer.domElement);

  const texture = await new Promise((resolve, reject) => {
    new THREE.TextureLoader().load(ATLAS, resolve, undefined, reject);
  });
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.anisotropy = Math.min(4, renderer.capabilities.getMaxAnisotropy());

  // One geometry, one material, one draw call: each instance picks its own
  // tile out of the atlas through an attribute, so ninety posters cost about
  // as much as one. Everything per-poster rides along as an attribute here.
  const geometry = new THREE.InstancedBufferGeometry();
  const plane = new THREE.PlaneGeometry(1, 1.5);
  // setAttribute, not direct assignment: three.js tracks attributes through
  // the setter, and a geometry whose attributes were assigned straight onto
  // the object uploads no buffers and silently draws nothing.
  geometry.setIndex(plane.getIndex());
  geometry.setAttribute('position', plane.getAttribute('position'));
  geometry.setAttribute('uv', plane.getAttribute('uv'));
  plane.dispose();

  const COUNT = 46;
  const offsets = new Float32Array(COUNT * 3);
  const tiles = new Float32Array(COUNT * 2);
  const phases = new Float32Array(COUNT);
  const scales = new Float32Array(COUNT);
  const tilts = new Float32Array(COUNT * 2);

  // Deterministic layout: a seeded generator keeps the composition identical
  // on every load, so the hero cannot render a visibly worse arrangement by
  // luck on someone's first visit.
  let seed = 20260903;
  const rand = () => {
    seed = (seed * 1664525 + 1013904223) % 4294967296;
    return seed / 4294967296;
  };

  for (let i = 0; i < COUNT; i++) {
    // The whole field sits behind the camera plane rather than straddling it.
    // Cards in front of z=0 fill the frame and swallow both the headline and
    // the product shot, which is the opposite of what a backdrop is for.
    const depth = -2 - rand() * 19;          // world z: -2 (near) .. -21 (far)
    const spread = 6.0 + (14 - depth) * 0.30;
    // Posters are pushed out of the middle of the frame rather than scattered
    // evenly: the headline sits there, and a poster drifting under a letter is
    // the one thing that makes a field like this look accidental. The bias is
    // horizontal only, so the wall still fills the top and bottom edges.
    // The gap has to be measured in what the camera sees, not in world units.
    // A perspective camera shrinks distant cards toward the centre, so a fixed
    // world-space push lets the far rows drift back under the headline exactly
    // where they must not be. Scaling the push by distance keeps the clear
    // corridor the same width all the way to the back of the field.
    const side = rand() < 0.5 ? -1 : 1;
    const push = 0.34 + rand() * 0.66;
    const perspective = (camera.position.z - depth) / camera.position.z;
    offsets[i * 3] = side * (4.2 + push * spread * 0.58) * perspective;
    offsets[i * 3 + 1] = (rand() - 0.5) * spread * 1.15;
    offsets[i * 3 + 2] = depth;
    const tile = Math.floor(rand() * TILES);
    tiles[i * 2] = tile % COLS;
    tiles[i * 2 + 1] = Math.floor(tile / COLS);
    phases[i] = rand() * Math.PI * 2;
    scales[i] = 2.2 + rand() * 1.7;
    // A few degrees of yaw and pitch per poster. Without this every card faces
    // the camera dead-on and the field reads as flat cut-outs on a backdrop;
    // the parallax alone cannot sell depth that the geometry denies. Cards
    // turn to face the centre of the frame, the way a physical wall would.
    tilts[i * 2] = -side * (0.16 + rand() * 0.22);
    tilts[i * 2 + 1] = (rand() - 0.5) * 0.20;
  }

  geometry.setAttribute('aOffset', new THREE.InstancedBufferAttribute(offsets, 3));
  geometry.setAttribute('aTile', new THREE.InstancedBufferAttribute(tiles, 2));
  geometry.setAttribute('aPhase', new THREE.InstancedBufferAttribute(phases, 1));
  geometry.setAttribute('aScale', new THREE.InstancedBufferAttribute(scales, 1));
  geometry.setAttribute('aTilt', new THREE.InstancedBufferAttribute(tilts, 2));
  geometry.instanceCount = COUNT;

  const uniforms = {
    uMap: { value: texture },
    uTime: { value: 0 },
    uScroll: { value: 0 },
    uPointer: { value: new THREE.Vector2(0, 0) },
    uGrid: { value: new THREE.Vector2(COLS, ROWS) },
    uFade: { value: 0.35 },
  };

  const material = new THREE.ShaderMaterial({
    uniforms,
    transparent: true,
    depthWrite: true,
    depthTest: true,
    blending: THREE.NormalBlending,
    vertexShader: [
      'attribute vec3 aOffset;',
      'attribute vec2 aTile;',
      'attribute float aPhase;',
      'attribute float aScale;',
      'attribute vec2 aTilt;',
      'uniform float uTime;',
      'uniform float uScroll;',
      'uniform vec2 uPointer;',
      'varying vec2 vUv;',
      'varying vec2 vTile;',
      'varying float vDepth;',
      'varying float vLit;',
      'varying vec2 vNdc;',
      'void main() {',
      '  vUv = uv;',
      '  vTile = aTile;',
      '  vec3 pos = position * aScale;',
      '  vLit = position.x + 0.5;',
      // Yaw then pitch, breathing by a few hundredths of a radian so the wall
      // never freezes into a still. The rotation happens in local space before
      // the instance offset, so each card turns about its own centre.
      '  float yaw = aTilt.x + sin(uTime * 0.19 + aPhase) * 0.035;',
      '  float pitch = aTilt.y + cos(uTime * 0.23 + aPhase) * 0.028;',
      '  float cy = cos(yaw), sy = sin(yaw);',
      '  pos = vec3(pos.x * cy + pos.z * sy, pos.y, -pos.x * sy + pos.z * cy);',
      '  float cp = cos(pitch), sp = sin(pitch);',
      '  pos = vec3(pos.x, pos.y * cp - pos.z * sp, pos.y * sp + pos.z * cp);',
      // The key light follows the card's own turn, so a poster angled away
      // from the centre falls off into shadow instead of staying evenly lit.
      '  vLit = mix(vLit, clamp(vLit + sy * 0.55, 0.0, 1.0), 0.85);',
      '  vec3 world = aOffset;',
      // Slow drift, phase-shifted per instance so the field never pulses in
      // unison, plus scroll pushing the whole wall toward the camera.
      '  world.y += sin(uTime * 0.22 + aPhase) * 0.35;',
      '  world.x += cos(uTime * 0.17 + aPhase) * 0.22;',
      '  world.z += uScroll * 12.0;',
      // Parallax: distant posters answer the pointer more than near ones,
      // which is what actually sells the depth.
      '  float para = (-world.z) * 0.014;',
      '  world.x += uPointer.x * para;',
      '  world.y += uPointer.y * para * 0.6;',
      // Posters that travel past the camera wrap to the back of the field
      // rather than vanishing, so the wall never runs out.
      // Wrap inside the same band the layout uses (-21..-2). A card that
      // drifts past the near edge reappears at the back, so the wall never
      // thins out, and it never crosses in front of the camera.
      '  world.z = mod(world.z + 21.0, 19.0) - 21.0;',
      '  vDepth = 14.0 - world.z;',
      '  vec4 clip = projectionMatrix * modelViewMatrix * vec4(pos + world, 1.0);',
      // How far this vertex lands from the centre of the screen, in NDC. The
      // headline occupies the middle band, and pushing cards apart in world
      // space cannot keep them out of it — perspective pulls distant cards
      // back toward the centre however far out they start. Measuring in clip
      // space is the only test that holds at every depth, so the fade is
      // computed here and applied to alpha in the fragment stage.
      '  float ndcX = clip.x / max(clip.w, 0.0001);',
      '  float ndcY = clip.y / max(clip.w, 0.0001);',
      // The copy is not centred — it occupies the right half of the frame,
      // roughly ndcX 0.03..0.75 and the middle band vertically. Fading by
      // distance from screen centre would leave exactly the region the
      // headline sits in at full strength, which is what happened. So the
      // quiet zone is an explicit rectangle around the text instead.
      '  vNdc = vec2(ndcX, ndcY);',
      '  gl_Position = clip;',
      '}',
    ].join('\n'),
    fragmentShader: [
      'uniform sampler2D uMap;',
      'uniform vec2 uGrid;',
      'uniform float uFade;',
      'varying vec2 vUv;',
      'varying vec2 vTile;',
      'varying float vDepth;',
      'varying float vLit;',
      'varying vec2 vNdc;',
      'void main() {',
      '  vec2 cell = vec2(vTile.x + vUv.x, (uGrid.y - 1.0 - vTile.y) + vUv.y) / uGrid;',
      '  vec3 rgb = texture2D(uMap, cell).rgb;',
      // A raking key light across each poster: the lit edge carries the gold
      // the rest of the page is built from, so the wall belongs to the brand
      // rather than looking like stock artwork parked behind the text.
      '  float key = smoothstep(0.0, 1.0, vLit);',
      '  rgb *= 0.82 + 0.62 * key;',
      '  rgb += vec3(0.96, 0.74, 0.38) * pow(smoothstep(0.62, 1.0, vLit), 2.0) * 0.30;',
      // Rounded corners, and a hairline rim so each poster reads as a card
      // with an edge instead of a soft rectangle bleeding into its neighbour.
      '  vec2 q = abs(vUv - 0.5) - vec2(0.5 - 0.045, 0.5 - 0.03);',
      '  float dist = length(max(q, 0.0)) - 0.045;',
      '  float mask = 1.0 - smoothstep(0.0, 0.010, dist);',
      '  float rim = smoothstep(0.016, 0.004, abs(dist + 0.006));',
      '  rgb += vec3(0.92, 0.72, 0.40) * rim * 0.30;',
      // Aerial perspective: near posters stay saturated, far ones sink into
      // the page background. This is what carries the depth once the field is
      // bright enough to actually see.
      '  float depthFade = smoothstep(8.0, 12.5, vDepth) * (1.0 - smoothstep(21.0, 34.0, vDepth));',
      '  rgb = mix(vec3(0.050, 0.057, 0.078), rgb, 0.58 + 0.42 * depthFade);',
      // The quiet zone is evaluated per fragment, not per vertex. A poster is
      // several world units across, so interpolating a vertex-side mask left
      // the middle of a large card bright even when its corners were inside
      // the zone — which is exactly how a title kept surfacing under the
      // headline. Screen position per pixel is the only version that holds.
      '  float inX = 1.0 - smoothstep(0.02, 0.22, abs(vNdc.x - 0.40) - 0.36);',
      '  float inY = 1.0 - smoothstep(0.05, 0.32, abs(vNdc.y) - 0.46);',
      '  float clear = 0.05 + 0.95 * (1.0 - inX * inY);',
      '  gl_FragColor = vec4(rgb, mask * (0.30 + 0.70 * depthFade) * 0.78 * clear * uFade);',
      '}',
    ].join('\n'),
  });

  const mesh = new THREE.Mesh(geometry, material);
  mesh.frustumCulled = false;
  scene.add(mesh);

  const pointer = { x: 0, y: 0 };
  const target = { x: 0, y: 0 };

  const resize = () => {
    const w = mount.clientWidth || window.innerWidth;
    const h = mount.clientHeight || window.innerHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  };

  const onPointer = (event) => {
    target.x = (event.clientX / window.innerWidth) * 2 - 1;
    target.y = -((event.clientY / window.innerHeight) * 2 - 1);
  };

  window.addEventListener('resize', resize, { passive: true });
  window.addEventListener('pointermove', onPointer, { passive: true });
  resize();

  // Paused whenever the hero is off-screen or the tab is hidden: a background
  // tab must not keep a GPU loop warm.
  let visible = true;
  const observer = new IntersectionObserver(
    (entries) => { visible = entries[0].isIntersecting; },
    { threshold: 0 },
  );
  observer.observe(mount);

  const clock = new THREE.Clock();
  let frame = 0;

  const render = () => {
    frame = requestAnimationFrame(render);
    if (!visible || document.hidden) return;

    const delta = Math.min(clock.getDelta(), 0.05);
    uniforms.uTime.value += delta;
    uniforms.uFade.value = Math.min(1, uniforms.uFade.value + delta * 0.55);

    pointer.x += (target.x - pointer.x) * 0.045;
    pointer.y += (target.y - pointer.y) * 0.045;
    uniforms.uPointer.value.set(pointer.x, pointer.y);

    const hero = mount.getBoundingClientRect();
    const progress = Math.min(1, Math.max(0, -hero.top / Math.max(1, hero.height)));
    uniforms.uScroll.value = progress;

    renderer.render(scene, camera);
  };
  render();

  return () => {
    cancelAnimationFrame(frame);
    observer.disconnect();
    window.removeEventListener('resize', resize);
    window.removeEventListener('pointermove', onPointer);
    geometry.dispose();
    material.dispose();
    texture.dispose();
    renderer.dispose();
    if (renderer.domElement.parentNode) renderer.domElement.remove();
  };
}
