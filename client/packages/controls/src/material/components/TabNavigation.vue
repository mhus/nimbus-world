<template>
  <div class="card bg-base-100 shadow-xl">
    <div class="card-body">
      <TabGroup>
        <TabList class="tabs tabs-boxed">
          <Tab v-slot="{ selected }" class="tab">
            <span :class="{ 'tab-active': selected }">Block Types</span>
          </Tab>
          <Tab v-slot="{ selected }" class="tab">
            <span :class="{ 'tab-active': selected }">Textures & Assets</span>
          </Tab>
        </TabList>

        <TabPanels class="mt-6">
          <TabPanel>
            <BlockTypeEditor />
          </TabPanel>
          <TabPanel>
            <AssetEditor />
          </TabPanel>
        </TabPanels>
      </TabGroup>
    </div>
  </div>
</template>

<script setup lang="ts">
import { TabGroup, TabList, Tab, TabPanels, TabPanel } from '@headlessui/vue';
import BlockTypeEditor from '../views/BlockTypeEditor.vue';
import AssetEditor from '../views/AssetEditor.vue';
</script>
