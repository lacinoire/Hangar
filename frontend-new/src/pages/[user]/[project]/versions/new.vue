<script lang="ts" setup>
import { useHead } from "@vueuse/head";
import { useSeo } from "~/composables/useSeo";
import { projectIconUrl } from "~/composables/useUrlHelper";
import { HangarProject, IPlatform, PendingVersion, ProjectChannel } from "hangar-internal";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import Steps, { Step } from "~/components/design/Steps.vue";
import { computed, reactive, Ref, ref } from "vue";
import Alert from "~/components/design/Alert.vue";
import InputFile from "~/components/ui/InputFile.vue";
import InputText from "~/components/ui/InputText.vue";
import InputSelect from "~/components/ui/InputSelect.vue";
import Button from "~/components/design/Button.vue";
import InputCheckbox from "~/components/ui/InputCheckbox.vue";
import MarkdownEditor from "~/components/MarkdownEditor.vue";
import { required, url as validUrl } from "~/composables/useValidationHelpers";
import { useInternalApi } from "~/composables/useApi";
import { Platform } from "~/types/enums";
import { handleRequestError } from "~/composables/useErrorHandling";
import { useContext } from "vite-ssr/vue";
import { formatSize } from "~/composables/useFile";
import ChannelModal from "~/components/modals/ChannelModal.vue";
import { remove } from "lodash-es";
import { useBackendDataStore } from "~/store/backendData";
import DependencyTable from "~/components/projects/DependencyTable.vue";

const route = useRoute();
const router = useRouter();
const ctx = useContext();
const i18n = useI18n();
const t = i18n.t;
const backendData = useBackendDataStore();
const props = defineProps<{
  project: HangarProject;
}>();

const selectedStep = ref("artifact");
const steps: Step[] = [
  {
    value: "artifact",
    header: t("version.new.steps.1.header"),
    beforeNext: async () => {
      return createPendingVersion();
    },
    disableNext: computed(() => file.value == null && url.value == null),
  },
  { value: "basic", header: t("version.new.steps.2.header") },
  { value: "dependencies", header: t("version.new.steps.3.header") },
  {
    value: "changelog",
    header: t("version.new.steps.4.header"),
    beforeNext: async () => {
      return createVersion();
    },
  },
];

const file = ref<File | null>();
const url = ref<string | null>();
const pendingVersion = ref<PendingVersion>();
const channels = ref<ProjectChannel[]>([]);
const selectedPlatforms = ref<Platform[]>([]);
const descriptionEditor = ref();
const loading = reactive({
  create: false,
  submit: false,
});

const isFile = computed(() => pendingVersion.value?.isFile);

const currentChannel = computed(() => channels.value.find((c) => c.name === pendingVersion.value?.channelName));

const platforms = computed<IPlatform[]>(() => {
  if (pendingVersion.value?.isFile) {
    const result: IPlatform[] = [];
    for (const platformDependenciesKey in pendingVersion.value.platformDependencies) {
      result.push(backendData.platforms!.get(platformDependenciesKey as Platform)!);
    }
    return result;
  }
  return [...backendData.platforms!.values()];
});

const platformsForPluginDeps = computed<Platform[]>(() => {
  const platforms: Platform[] = [];
  if (pendingVersion.value?.isFile) {
    for (const key of Object.keys(pendingVersion.value!.pluginDependencies)) {
      if (pendingVersion.value?.pluginDependencies[key as Platform].length) {
        platforms.push(key as Platform);
      }
    }
  } else {
    platforms.push(...selectedPlatforms.value);
  }
  return platforms;
});

const platformVersionRules = computed(() => {
  return !pendingVersion.value?.isFile ? [] : [(v: string[]) => !!v.length || "Error"];
});

async function createPendingVersion() {
  loading.create = true;
  const data: FormData = new FormData();
  if (url.value) {
    data.append("url", url.value);
  } else if (file.value) {
    data.append("pluginFile", file.value);
  } else {
    return false;
  }
  channels.value = await useInternalApi<ProjectChannel[]>(`channels/${route.params.user}/${route.params.project}`, false).catch<any>((e) =>
    handleRequestError(e, ctx, i18n)
  );
  pendingVersion.value = await useInternalApi<PendingVersion>(`versions/version/${props.project.id}/upload`, true, "post", data).catch<any>((e) =>
    handleRequestError(e, ctx, i18n)
  );
  for (const platformDependenciesKey in pendingVersion.value?.platformDependencies) {
    if (pendingVersion.value?.platformDependencies[platformDependenciesKey as Platform].length) {
      selectedPlatforms.value.push(platformDependenciesKey as Platform);
    }
  }
  loading.create = false;
  return pendingVersion.value !== undefined;
}

async function createVersion() {
  if (!pendingVersion.value) return false;
  loading.submit = true;
  pendingVersion.value.description = descriptionEditor.value.rawEdited;
  pendingVersion.value.channelColor = currentChannel.value!.color;
  pendingVersion.value.channelNonReviewed = currentChannel.value!.nonReviewed;
  // played around trying to get this to happen in jackson's deserialization, but couldn't figure it out.
  for (const platform in pendingVersion.value.platformDependencies) {
    if (pendingVersion.value.platformDependencies[platform as Platform].length < 1) {
      delete pendingVersion.value.platformDependencies[platform as Platform];
    }
  }
  for (const platform in pendingVersion.value.pluginDependencies) {
    if (pendingVersion.value.pluginDependencies[platform as Platform].length < 1) {
      delete pendingVersion.value.pluginDependencies[platform as Platform];
    }
  }
  console.log("pending", pendingVersion.value);
  console.log("editor", descriptionEditor.value);
  console.log("rawEdited", descriptionEditor.value.rawEdited);
  try {
    await useInternalApi(`versions/version/${props.project.id}/create`, true, "post", pendingVersion.value);
    await router.push(`/${route.params.user}/${route.params.project}/versions`);
    return true;
  } catch (e: any) {
    handleRequestError(e, ctx, i18n);
    return false;
  } finally {
    loading.submit = false;
  }
}

function addChannel(channel: ProjectChannel) {
  if (pendingVersion.value) {
    remove(channels.value, (c) => c.temp);
    channels.value.push(Object.assign({}, channel));
    pendingVersion.value.channelName = channel.name;
  }
}

function togglePlatformVersion(value: string[], platform: Platform) {
  if (value.length === 0 && selectedPlatforms.value.includes(platform)) {
    delete selectedPlatforms.value[selectedPlatforms.value.indexOf(platform)];
  } else if (!selectedPlatforms.value.includes(platform)) {
    selectedPlatforms.value.push(platform);
  }
}

useHead(
  useSeo(
    i18n.t("version.new.title") + " | " + props.project.name,
    props.project.description,
    route,
    projectIconUrl(props.project.namespace.owner, props.project.namespace.slug)
  )
);
</script>

<!-- todo functionality, design, i18n, validation, all the things -->
<template>
  <Steps v-model="selectedStep" :steps="steps" button-lang-key="version.new.steps.">
    <template #artifact>
      <p>Please specify the artifact. You can either upload a jar or a zip file, or you can link to an external site.</p>
      <Alert class="my-4 text-white" type="info">An external link needs to be a direct download link!</Alert>
      <div class="flex flex-wrap">
        <InputFile v-model="file" accept=".jar,.zip" />
        <span class="basis-full my-3">or</span>
        <InputText v-model="url" :label="t('version.new.form.externalUrl')" :rules="[validUrl()]" />
      </div>
    </template>
    <template #basic>
      <p>We detected the following settings based on the artifact you provided. Please fill out the remaining fields.</p>
      <div class="flex flex-wrap">
        <!-- TODO validate version string against existing versions. complex because they only have to be unique per-platform -->
        <div :class="'basis-full mt-2 ' + (isFile ? 'md:basis-4/12' : 'md:basis-6/12')">
          <InputText v-model="pendingVersion.versionString" :label="t('version.new.form.versionString')" :rules="[required()]" :disabled="isFile" />
        </div>
        <div v-if="isFile" :class="'basis-full mt-2 md:basis-4/12'">
          <InputText :model-value="pendingVersion.fileInfo.name" :label="t('version.new.form.fileName')" disabled />
        </div>
        <div v-if="isFile" :class="'basis-full mt-2 md:basis-4/12'">
          <InputText :model-value="formatSize(pendingVersion.fileInfo.sizeBytes)" :label="t('version.new.form.fileSize')" disabled />
        </div>
        <div v-if="!isFile" :class="'basis-full mt-2 md:basis-6/12'">
          <InputText v-model="pendingVersion.externalUrl" :label="t('version.new.form.externalUrl')" />
        </div>

        <div class="basis-8/12 mt-2">
          <InputSelect v-model="pendingVersion.channelName" :values="channels" item-text="name" item-value="name" :label="t('version.new.form.channel')" />
        </div>
        <div class="basis-4/12 mt-2">
          <ChannelModal :project-id="project.id" @create="addChannel">
            <template #activator="{ on, attrs }">
              <Button class="basis-4/12 mt-2" v-bind="attrs" v-on="on">
                {{ t("version.new.form.addChannel") }}
                <IconMdiPlus />
              </Button>
            </template>
          </ChannelModal>
        </div>

        <div class="basis-4/12 mt-2">
          <InputCheckbox v-model="pendingVersion.unstable" :label="t('version.new.form.unstable')" />
        </div>
        <div class="basis-4/12 mt-2">
          <InputCheckbox v-model="pendingVersion.recommended" :label="t('version.new.form.recommended')" />
        </div>
        <div class="basis-4/12 mt-2">
          <InputCheckbox v-model="pendingVersion.forumPost" :label="t('version.new.form.forumPost')" />
        </div>
      </div>
    </template>
    <template #dependencies>
      <p>We detected the following dependencies based on the artifact you provided. Please fill out the remaining fields.</p>
      <h2 class="text-xl mt-2">{{ t("version.new.form.platforms") }}</h2>

      <div class="flex flex-wrap">
        <div v-for="platform in platforms" :key="platform.name" class="basis-full">
          <div>{{ platform.name }}</div>
          <div class="flex flex-wrap">
            <div v-for="version in platform.possibleVersions" :key="`${platform.name}-${version}`" class="ml-2">
              <InputCheckbox
                v-model="pendingVersion.platformDependencies[platform.enumName]"
                :rules="platformVersionRules"
                :label="version"
                :value="version"
                @change="togglePlatformVersion($event, platform.enumName)"
              />
            </div>
          </div>
        </div>
      </div>

      <h2 class="text-xl mt-4">{{ t("version.new.form.dependencies") }}</h2>
      <div v-for="platform in platformsForPluginDeps" :key="platform" class="basis-full">
        <div>{{ backendData.platforms.get(platform).name }}</div>
        <DependencyTable
          :key="`${platform}-deps-table`"
          :platform="platform"
          :version="pendingVersion"
          :no-editing="pendingVersion.isFile"
          :new-deps-prop="pendingVersion.pluginDependencies[platform]"
          :is-new="!pendingVersion.isFile"
        />
      </div>
    </template>
    <template #changelog>
      <p>Whats new?</p>
      <MarkdownEditor
        ref="descriptionEditor"
        class="mt-2"
        :raw="pendingVersion.description"
        editing
        :deletable="false"
        :cancellable="false"
        :saveable="false"
        :rules="[required(t('version.new.form.release.bulletin'))]"
      />
    </template>
  </Steps>
</template>

<route lang="yaml">
meta:
  requireProjectPerm: ["CREATE_VERSION"]
</route>
