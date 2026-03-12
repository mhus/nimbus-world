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
import '@babylonjs/loaders/glTF';
import { apiService } from '@/services/ApiService';

const props = defineProps<{
  modelUrl: string;
}>();

const containerRef = ref<HTMLDivElement>();
const canvasRef = ref<HTMLCanvasElement>();
const loadingModel = ref(false);
const errorMsg = ref('');

let engine: Engine | null = null;
let scene: Scene | null = null;
let camera: ArcRotateCamera | null = null;
let currentModelNodes: any[] = [];

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
let currentBlobUrl: string | null = null;

async function loadModel(url: string) {
  if (!scene || !url) return;

  // Remove previous model
  for (const node of currentModelNodes) {
    node.dispose();
  }
  currentModelNodes = [];
  if (currentBlobUrl) {
    URL.revokeObjectURL(currentBlobUrl);
    currentBlobUrl = null;
  }
  errorMsg.value = '';

  loadingModel.value = true;
  try {
    // Fetch GLB via axios (has withCredentials for cookie auth)
    const response = await apiService.getClient().get(url, { responseType: 'arraybuffer' });
    const blob = new Blob([response.data], { type: 'model/gltf-binary' });
    currentBlobUrl = URL.createObjectURL(blob);

    const result = await SceneLoader.ImportMeshAsync('', currentBlobUrl, '', scene, undefined, '.glb');

    currentModelNodes = [...result.meshes, ...result.transformNodes, ...result.skeletons];

    // Stop all animations
    scene.stopAllAnimations();
    for (const group of result.animationGroups || []) {
      group.stop();
      group.dispose();
    }

    // Compute bounding box of all meshes
    let min = new Vector3(Infinity, Infinity, Infinity);
    let max = new Vector3(-Infinity, -Infinity, -Infinity);
    for (const mesh of result.meshes) {
      if (!mesh.getBoundingInfo) continue;
      mesh.refreshBoundingInfo();
      const bi = mesh.getBoundingInfo();
      min = Vector3.Minimize(min, bi.boundingBox.minimumWorld);
      max = Vector3.Maximize(max, bi.boundingBox.maximumWorld);
    }

    const center = min.add(max).scale(0.5);
    const extent = max.subtract(min);
    const maxDim = Math.max(extent.x, extent.y, extent.z);

    // Center camera on model
    if (camera) {
      camera.target = center;
      camera.radius = maxDim * 1.8;
      camera.alpha = Math.PI / 4;
      camera.beta = Math.PI / 3;
    }
  } catch (e: any) {
    console.error('ModelPreview: failed to load', url, e);
    errorMsg.value = 'Modell konnte nicht geladen werden';
  } finally {
    loadingModel.value = false;
  }
}

watch(() => props.modelUrl, (newUrl) => {
  if (newUrl) {
    loadModel(newUrl);
  } else {
    for (const node of currentModelNodes) {
      node.dispose();
    }
    currentModelNodes = [];
    if (currentBlobUrl) {
      URL.revokeObjectURL(currentBlobUrl);
      currentBlobUrl = null;
    }
  }
});

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
  engine?.dispose();
  engine = null;
  scene = null;
  camera = null;
  currentModelNodes = [];
  if (currentBlobUrl) {
    URL.revokeObjectURL(currentBlobUrl);
    currentBlobUrl = null;
  }
});
</script>
