<template>
  <div ref="containerRef" class="relative rounded-lg overflow-hidden bg-gray-900 border border-gray-600">
    <canvas ref="canvasRef" class="w-full h-full block" />
    <div v-if="loadingModel" class="absolute inset-0 flex items-center justify-center bg-gray-900/60">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-emerald-400"></div>
    </div>
    <div v-if="errorMsg" class="absolute inset-0 flex items-center justify-center bg-gray-900/60">
      <p class="text-red-400 text-xs text-center px-2">{{ errorMsg }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { Engine } from '@babylonjs/core/Engines/engine';
import { Scene } from '@babylonjs/core/scene';
import { ArcRotateCamera } from '@babylonjs/core/Cameras/arcRotateCamera';
import { HemisphericLight } from '@babylonjs/core/Lights/hemisphericLight';
import { DirectionalLight } from '@babylonjs/core/Lights/directionalLight';
import { Vector3 } from '@babylonjs/core/Maths/math.vector';
import { Color4, Color3 } from '@babylonjs/core/Maths/math.color';
import { SceneLoader } from '@babylonjs/core/Loading/sceneLoader';
import type { AnimationGroup } from '@babylonjs/core/Animations/animationGroup';
import type { AbstractMesh } from '@babylonjs/core/Meshes/abstractMesh';
import type { Skeleton, Bone } from '@babylonjs/core/Bones';
import type { PBRMaterial } from '@babylonjs/core/Materials/PBR/pbrMaterial';
import '@babylonjs/loaders/glTF';
import { apiService } from '@/services/ApiService';

const props = defineProps<{
  modelUrl: string;
  modifierMapping: Record<string, string>;
  modifierValues: Record<string, string>;
}>();

const emit = defineEmits<{
  animationsLoaded: [names: string[]];
  modelInfoLoaded: [info: { boneNames: string[]; materialNames: string[] }];
}>();

const containerRef = ref<HTMLDivElement>();
const canvasRef = ref<HTMLCanvasElement>();
const loadingModel = ref(false);
const errorMsg = ref('');

let engine: Engine | null = null;
let scene: Scene | null = null;
let camera: ArcRotateCamera | null = null;
let currentModelNodes: any[] = [];
let currentBlobUrl: string | null = null;
let loadedMeshes: AbstractMesh[] = [];
let loadedSkeletons: Skeleton[] = [];
let loadedAnimationGroups: AnimationGroup[] = [];
const originalMaterials = new Map<AbstractMesh, any>();

function setupScene() {
  if (!canvasRef.value) return;

  engine = new Engine(canvasRef.value, true, {
    preserveDrawingBuffer: false,
    stencil: false,
    antialias: true,
  });

  scene = new Scene(engine);
  scene.clearColor = new Color4(0.12, 0.12, 0.15, 1);

  camera = new ArcRotateCamera('previewCam', Math.PI / 4, Math.PI / 3, 5, Vector3.Zero(), scene);
  camera.lowerRadiusLimit = 1;
  camera.upperRadiusLimit = 20;
  camera.wheelPrecision = 30;
  camera.panningSensibility = 200;
  camera.attachControl(canvasRef.value, true);

  const hemiLight = new HemisphericLight('hemi', new Vector3(0, 1, 0), scene);
  hemiLight.intensity = 0.6;
  hemiLight.groundColor = new Color3(0.3, 0.3, 0.35);

  const dirLight = new DirectionalLight('dir', new Vector3(-1, -2, 1).normalize(), scene);
  dirLight.intensity = 0.8;

  engine.runRenderLoop(() => {
    scene?.render();
  });

  handleResize();
}

function handleResize() {
  if (!engine || !canvasRef.value || !containerRef.value) return;
  const rect = containerRef.value.getBoundingClientRect();
  canvasRef.value.width = rect.width * window.devicePixelRatio;
  canvasRef.value.height = rect.height * window.devicePixelRatio;
  engine.resize();
}

let resizeObserver: ResizeObserver | null = null;
let clonedMaterials: any[] = [];

async function loadModel(url: string) {
  if (!scene || !url) return;

  // Remove previous model
  for (const node of currentModelNodes) {
    node.dispose();
  }
  currentModelNodes = [];
  loadedMeshes = [];
  loadedSkeletons = [];
  loadedAnimationGroups = [];
  originalMaterials.clear();
  disposeMaterialClones();

  if (currentBlobUrl) {
    URL.revokeObjectURL(currentBlobUrl);
    currentBlobUrl = null;
  }
  errorMsg.value = '';

  loadingModel.value = true;
  try {
    const response = await apiService.getClient().get(url, { responseType: 'arraybuffer' });
    const blob = new Blob([response.data], { type: 'model/gltf-binary' });
    currentBlobUrl = URL.createObjectURL(blob);

    const result = await SceneLoader.ImportMeshAsync('', currentBlobUrl, '', scene, undefined, '.glb');

    currentModelNodes = [...result.meshes, ...result.transformNodes, ...result.skeletons];
    loadedSkeletons = result.skeletons || [];

    // Collect all meshes including children
    loadedMeshes = [];
    for (const mesh of result.meshes) {
      loadedMeshes.push(mesh);
    }

    // Store original materials for re-applying
    for (const mesh of loadedMeshes) {
      if (mesh.material) {
        originalMaterials.set(mesh, mesh.material);
      }
    }

    // Stop all animations but keep them for playback
    loadedAnimationGroups = result.animationGroups || [];
    for (const group of loadedAnimationGroups) {
      group.stop();
    }
    emit('animationsLoaded', loadedAnimationGroups.map(g => g.name));

    // Emit bone and material names
    const boneNames = new Set<string>();
    for (const skeleton of loadedSkeletons) {
      for (const bone of skeleton.bones) {
        if (bone.name) boneNames.add(bone.name);
      }
    }
    const materialNames = new Set<string>();
    for (const mesh of loadedMeshes) {
      if (mesh.material?.name) materialNames.add(mesh.material.name);
    }
    emit('modelInfoLoaded', {
      boneNames: [...boneNames].sort(),
      materialNames: [...materialNames].sort(),
    });

    // Compute bounding box
    let min = new Vector3(Infinity, Infinity, Infinity);
    let max = new Vector3(-Infinity, -Infinity, -Infinity);
    for (const mesh of result.meshes) {
      if (!mesh.getBoundingInfo) continue;
      mesh.refreshBoundingInfo({});
      const bi = mesh.getBoundingInfo();
      min = Vector3.Minimize(min, bi.boundingBox.minimumWorld);
      max = Vector3.Maximize(max, bi.boundingBox.maximumWorld);
    }

    const center = min.add(max).scale(0.5);
    const extent = max.subtract(min);
    const maxDim = Math.max(extent.x, extent.y, extent.z);

    if (camera) {
      camera.target = center;
      camera.radius = maxDim * 1.8;
      camera.alpha = Math.PI / 4;
      camera.beta = Math.PI / 3;
    }

    // Apply current modifiers
    applyModifiers();
  } catch (e: any) {
    console.error('ModelPreview: failed to load', url, e);
    errorMsg.value = 'Modell konnte nicht geladen werden';
  } finally {
    loadingModel.value = false;
  }
}

function disposeMaterialClones() {
  for (const mat of clonedMaterials) {
    mat.dispose();
  }
  clonedMaterials = [];
}

function applyModifiers() {
  if (loadedMeshes.length === 0) return;

  // Reset: restore original materials
  disposeMaterialClones();
  for (const [mesh, originalMat] of originalMaterials) {
    mesh.material = originalMat;
  }

  const mapping = props.modifierMapping;
  const values = props.modifierValues;
  if (!mapping || !values) return;

  const clonedMaterialMap = new Map<string, PBRMaterial>();

  for (const [modifierKey, value] of Object.entries(values)) {
    if (!value || value.trim() === '') continue;
    const mappingStr = mapping[modifierKey];
    if (!mappingStr) continue;

    const descriptors = parseDescriptors(mappingStr);
    for (const desc of descriptors) {
      if (desc.category === 'bone') {
        applyBone(desc, value);
      } else if (desc.category === 'color') {
        applyColor(desc, value, clonedMaterialMap);
      }
    }
  }

  clonedMaterials = Array.from(clonedMaterialMap.values());
}

interface Descriptor { category: string; targetName: string; property: string; }

function parseDescriptors(mapping: string): Descriptor[] {
  return mapping.split(';').map(part => {
    const s = part.trim().split(':');
    if (s.length !== 3) return null;
    return { category: s[0], targetName: s[1], property: s[2] };
  }).filter((d): d is Descriptor => d !== null);
}

function parseColor(value: string): Color3 {
  if (value.startsWith('#') && value.length === 7) {
    const r = parseInt(value.substring(1, 3), 16) / 255;
    const g = parseInt(value.substring(3, 5), 16) / 255;
    const b = parseInt(value.substring(5, 7), 16) / 255;
    if (!isNaN(r) && !isNaN(g) && !isNaN(b)) return new Color3(r, g, b);
  }
  if (value.includes(',')) {
    const parts = value.split(',').map(s => parseFloat(s.trim()));
    if (parts.length === 3 && parts.every(p => !isNaN(p))) return new Color3(parts[0], parts[1], parts[2]);
  }
  return new Color3(1, 1, 1);
}

function parseScale(value: string): Vector3 {
  if (value.includes(',')) {
    const parts = value.split(',').map(s => parseFloat(s.trim()));
    if (parts.length === 3 && parts.every(p => !isNaN(p))) return new Vector3(parts[0], parts[1], parts[2]);
  }
  const u = parseFloat(value);
  if (!isNaN(u)) return new Vector3(u, u, u);
  return new Vector3(1, 1, 1);
}

function applyBone(desc: Descriptor, value: string) {
  if (desc.property !== 'scale') return;
  const scale = parseScale(value);
  for (const skeleton of loadedSkeletons) {
    const bone = skeleton.bones.find((b: Bone) => b.name === desc.targetName);
    if (bone) {
      const tn = bone.getTransformNode();
      if (tn) {
        tn.scaling = scale;
      } else {
        bone.scaling = scale;
      }
      break;
    }
  }
}

function applyColor(desc: Descriptor, value: string, clonedMap: Map<string, PBRMaterial>) {
  if (desc.property !== 'tint' && desc.property !== 'baseColor') return;
  const color = parseColor(value);

  for (const mesh of loadedMeshes) {
    const mat = mesh.material;
    if (!mat || !mat.name.includes(desc.targetName)) continue;

    const originalName = mat.name;
    let cloned = clonedMap.get(originalName);
    if (!cloned) {
      cloned = (mat as PBRMaterial).clone(`${originalName}_preview`) as PBRMaterial;
      clonedMap.set(originalName, cloned);
    }

    if (desc.property === 'tint') {
      const existing = cloned.albedoColor || new Color3(1, 1, 1);
      cloned.albedoColor = new Color3(existing.r * color.r, existing.g * color.g, existing.b * color.b);
    } else {
      cloned.albedoColor = color;
    }

    mesh.material = cloned;
  }
}

function playAnimation(name: string, loop: boolean = false, speed: number = 1.0) {
  // Stop all current animations
  for (const group of loadedAnimationGroups) {
    group.stop();
  }
  if (!name) return;
  const group = loadedAnimationGroups.find(g => g.name === name);
  if (group) {
    group.speedRatio = speed;
    group.loopAnimation = loop;
    group.start(loop);
  }
}

function stopAnimations() {
  for (const group of loadedAnimationGroups) {
    group.stop();
  }
}

function getAnimationNames(): string[] {
  return loadedAnimationGroups.map(g => g.name);
}

defineExpose({ playAnimation, stopAnimations, getAnimationNames });

// Watch for model URL changes
watch(() => props.modelUrl, (newUrl) => {
  if (newUrl) {
    loadModel(newUrl);
  } else {
    for (const node of currentModelNodes) {
      node.dispose();
    }
    currentModelNodes = [];
    loadedMeshes = [];
    loadedSkeletons = [];
    loadedAnimationGroups = [];
    originalMaterials.clear();
    disposeMaterialClones();
    if (currentBlobUrl) {
      URL.revokeObjectURL(currentBlobUrl);
      currentBlobUrl = null;
    }
  }
});

// Watch for modifier value changes and re-apply
watch(() => props.modifierValues, () => {
  applyModifiers();
}, { deep: true });

onMounted(async () => {
  await nextTick();
  setupScene();

  resizeObserver = new ResizeObserver(() => handleResize());
  if (containerRef.value) {
    resizeObserver.observe(containerRef.value);
  }

  if (props.modelUrl) {
    loadModel(props.modelUrl);
  }
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  disposeMaterialClones();
  engine?.dispose();
  engine = null;
  scene = null;
  camera = null;
  currentModelNodes = [];
  loadedMeshes = [];
  loadedSkeletons = [];
  loadedAnimationGroups = [];
  originalMaterials.clear();
  if (currentBlobUrl) {
    URL.revokeObjectURL(currentBlobUrl);
    currentBlobUrl = null;
  }
});
</script>
