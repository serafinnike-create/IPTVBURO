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

  // Deterministic layout: a seeded generator keeps the composition identical
  // on every load, so the hero cannot render a visibly worse arrangement by
  // luck on someone's first visit.
  let seed = 20260903;
  const rand = () => {
    seed = (seed * 1664525 + 1013904223) % 4294967296;
    return seed / 4294967296;
  };

  for (let i = 0; i < COUNT; i++) {
    const depth = 5 - rand() * 22;           // world z: +5 (near) .. -17 (far)
    const spread = 6.0 + (14 - depth) * 0.30;
    offsets[i * 3] = (rand() - 0.5) * spread * 2;
    offsets[i * 3 + 1] = (rand() - 0.5) * spread * 1.1;
    offsets[i * 3 + 2] = depth;
    const tile = Math.floor(rand() * TILES);
    tiles[i * 2] = tile % COLS;
    tiles[i * 2 + 1] = Math.floor(tile / COLS);
    phases[i] = rand() * Math.PI * 2;
    scales[i] = 2.6 + rand() * 2.2;
  }

  geometry.setAttribute('aOffset', new THREE.InstancedBufferAttribute(offsets, 3));
  geometry.setAttribute('aTile', new THREE.InstancedBufferAttribute(tiles, 2));
  geometry.setAttribute('aPhase', new THREE.InstancedBufferAttribute(phases, 1));
  geometry.setAttribute('aScale', new THREE.InstancedBufferAttribute(scales, 1));
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
      'uniform float uTime;',
      'uniform float uScroll;',
      'uniform vec2 uPointer;',
      'varying vec2 vUv;',
      'varying vec2 vTile;',
      'varying float vDepth;',
      'varying float vLit;',
      'void main() {',
      '  vUv = uv;',
      '  vTile = aTile;',
      '  vec3 pos = position * aScale;',
      '  vLit = position.x + 0.5;',
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
      '  world.z = mod(world.z + 17.0, 22.0) - 17.0;',
      '  vDepth = 14.0 - world.z;',
      '  gl_Position = projectionMatrix * modelViewMatrix * vec4(pos + world, 1.0);',
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
      'void main() {',
      '  vec2 cell = vec2(vTile.x + vUv.x, (uGrid.y - 1.0 - vTile.y) + vUv.y) / uGrid;',
      '  vec3 rgb = texture2D(uMap, cell).rgb;',
      '  rgb *= 0.74 + 0.55 * smoothstep(0.0, 1.0, vLit);',
      '  rgb += vec3(0.84, 0.66, 0.34) * pow(smoothstep(0.75, 1.0, vLit), 2.0) * 0.22;',
      '  vec2 q = abs(vUv - 0.5) - vec2(0.5 - 0.045, 0.5 - 0.03);',
      '  float mask = 1.0 - smoothstep(0.0, 0.012, length(max(q, 0.0)) - 0.045);',
      '  float depthFade = smoothstep(9.0, 13.0, vDepth) * (1.0 - smoothstep(20.0, 33.0, vDepth));',
      '  rgb = mix(vec3(0.055, 0.062, 0.082), rgb, 0.40 + 0.42 * depthFade);',
      '  gl_FragColor = vec4(rgb, mask * (0.22 + 0.78 * depthFade) * 0.34 * uFade);',
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
