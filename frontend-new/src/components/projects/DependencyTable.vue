<script lang="ts" setup>
import { DependencyVersion, PluginDependency, ProjectNamespace } from "hangar-api";
import { Platform } from "~/types/enums";
import Table from "~/components/design/Table.vue";
import { useI18n } from "vue-i18n";
import Button from "~/components/design/Button.vue";
import InputCheckbox from "~/components/ui/InputCheckbox.vue";
import InputText from "~/components/ui/InputText.vue";
import { required } from "~/composables/useValidationHelpers";
import { ref } from "vue";

const i18n = useI18n();
const t = i18n.t;

const props = withDefaults(
  defineProps<{
    version: DependencyVersion;
    platform: Platform;
    noEditing?: boolean;
    isNew?: boolean;
  }>(),
  {
    noEditing: false,
    isNew: false,
  }
);

const results = ref<Record<string, ProjectNamespace[]>>({});
const newDepResults = ref<ProjectNamespace[][]>([]);
const newDeps = ref<PluginDependency[]>([]);
</script>

<template>
  <Table>
    <thead>
      <tr>
        <th>{{ t("general.name") }}</th>
        <th>{{ t("general.required") }}</th>
        <th>{{ t("general.link") }}</th>
        <th v-if="!noEditing">
          {{ t("general.delete") }}
        </th>
      </tr>
    </thead>
    <tbody>
      <template v-if="!isNew">
        <tr v-for="(dep, index) in version.pluginDependencies[platform]" :key="`${platform}-${dep.name}`">
          <td>{{ dep.name }}</td>
          <td><InputCheckbox v-model="dep.required" /></td>
          <td>
            <InputText
              v-model.trim="dep.externalUrl"
              :placeholder="t('version.new.form.externalUrl')"
              :disabled="dep.namespace !== null && Object.keys(dep.namespace).length !== 0"
              :rules="dep.namespace !== null && Object.keys(dep.namespace).length !== 0 ? [] : [required(t('version.new.form.externalUrl'))]"
              clearable
            />
            <v-autocomplete
              v-model="dep.namespace"
              dense
              hide-details
              hide-no-data
              :placeholder="t('version.new.form.hangarProject')"
              class="mb-2"
              :items="results[dep.name]"
              :item-text="getNamespace"
              :item-value="getNamespace"
              return-object
              clearable
              auto-select-first
              :disabled="!!dep.externalUrl"
              :rules="!!dep.externalUrl ? [] : [required(t('version.new.form.hangarProject'))]"
              @update:search-input="onSearch($event, dep.name)"
            />
          </td>
          <td v-if="!noEditing">
            <Button icon color="error" @click="deleteDep(index)">
              <IconMdiDelete />
            </Button>
          </td>
        </tr>
      </template>

      <template v-if="!noEditing || isNew">
        <tr v-for="(newDep, index) in newDeps" :key="`newDep-${index}`">
          <td>
            <InputText
              v-model.trim="newDep.name"
              dense
              hide-details
              flat
              :label="t('general.name')"
              :rules="[required(t('general.name'))]"
              :disabled="noEditing"
            />
          </td>
          <td><InputCheckbox v-model="newDep.required" :ripple="false" /></td>
          <td>
            <InputText
              v-model.trim="newDep.externalUrl"
              :placeholder="t('version.new.form.externalUrl')"
              :disabled="newDep.namespace !== null && Object.keys(newDep.namespace).length !== 0"
              :rules="newDep.namespace !== null && Object.keys(newDep.namespace).length !== 0 ? [] : [required(t('version.new.form.externalUrl'))]"
              clearable
            />
            <v-autocomplete
              v-model="newDep.namespace"
              dense
              hide-details
              hide-no-data
              :placeholder="t('version.new.form.hangarProject')"
              class="mb-2"
              :items="newDepResults[index]"
              :item-text="getNamespace"
              :item-value="getNamespace"
              return-object
              clearable
              auto-select-first
              :disabled="!!newDep.externalUrl"
              :rules="!!newDep.externalUrl ? [] : [required(t('version.new.form.hangarProject'))]"
              @update:search-input="onNewDepSearch($event, index)"
            />
          </td>
          <td v-if="!noEditing">
            <Button button-type="red" @click="deleteNewDep(index)">
              <IconMdiDelete />
            </Button>
          </td>
        </tr>
      </template>
    </tbody>
  </Table>
  <div v-if="!noEditing" class="m-2">
    <Button block @click="addNewDep">
      <IconMdiPlus />
      {{ t("general.add") }}
    </Button>
  </div>
</template>
