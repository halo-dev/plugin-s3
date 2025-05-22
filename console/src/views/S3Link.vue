<script setup lang="ts">
import { computed, getCurrentInstance, onMounted, ref, watch } from "vue";
import {
  IconCheckboxCircle,
  IconRefreshLine,
  Toast,
  VButton,
  VCard,
  VEmpty,
  VEntity,
  VEntityContainer,
  VEntityField,
  VLoading,
  VModal,
  VPageHeader,
  VSpace,
  VStatusDot,
  VTag,
} from "@halo-dev/components";
import CarbonFolderDetailsReference from "~icons/carbon/folder-details-reference";
import IconErrorWarning from "~icons/ri/error-warning-line";
import { axiosInstance, coreApiClient, type Group } from "@halo-dev/api-client";
import { S3LinkControllerApi } from "@/api";
import type { S3ListResult, LinkResultItem, Policy, ObjectVo } from "@/api";

const s3LinkControllerApi = new S3LinkControllerApi(
  undefined,
  axiosInstance.defaults.baseURL,
  axiosInstance
);

const componentInstance = getCurrentInstance();
const t = (key: string) => {
  // @ts-ignore
  if (typeof componentInstance?.proxy?.$t === "function") {
    // @ts-ignore
    return componentInstance.proxy.$t(key);
  }
  return key;
};

const selectedFiles = ref<string[]>([]);
const policyName = ref<string>("");
const page = ref(1);
const size = ref(50);
const selectedGroup = ref("");
const policyOptions = ref<{ label: string; value: string; attrs: any }[]>([
  {
    label: "请选择存储策略",
    value: "",
    attrs: { disabled: true },
  },
]);
const defaultGroup = ref<Group>({
  apiVersion: "",
  kind: "",
  metadata: {
    name: "",
  },
  spec: {
    displayName: t("core.attachment.common.text.ungrouped"),
  },
});
// update when fetch first page
const filePrefix = ref<string>("");
// update when user input
const filePrefixBind = ref<string>("");
const s3Objects = ref<S3ListResult>({
  objects: [],
  hasMore: false,
  currentToken: "",
  nextToken: "",
  currentContinuationObject: "",
  nextContinuationObject: "",
});
const customGroups = ref<Group[]>([]);
// view state
const isFetching = ref(false);
const isShowModal = ref(false);
const isLinking = ref(false);
const isFetchingPolicies = ref(true);

const linkTips = ref("");
const linkFailedTable = ref<LinkResultItem[]>([]);
const linkedStatusItems: { label: string; value?: boolean }[] = [
  { label: "全部" },
  { label: "未关联", value: true },
];

// action state
const checkedAll = ref(false);
const selectedLinkedStatusItem = ref<boolean | undefined>(
  linkedStatusItems[0].value
);

const emptyTips = computed(() => {
  if (isFetchingPolicies.value) {
    return "正在加载存储策略";
  }
  if (policyOptions.value.length <= 1) {
    return "没有可用的存储策略，请前往【附件】添加S3存储策略";
  }
  if (!policyName.value) {
    return "请在左上方选择存储策略";
  }
  return "该存储策略的 桶/文件夹 下没有文件";
});

const handleCheckAllChange = (e: Event) => {
  const { checked } = e.target as HTMLInputElement;

  if (checked) {
    selectedFiles.value =
      s3Objects.value.objects
        ?.filter((file) => !file.isLinked)
        .map((file) => {
          return file.key || "";
        }) || [];
  } else {
    selectedFiles.value.length = 0;
    checkedAll.value = false;
  }
};

const fetchPolicies = async () => {
  try {
    const { status, data } = await s3LinkControllerApi.listS3Policies();
    if (status === 200) {
      policyOptions.value = [
        {
          label: "请选择存储策略",
          value: "",
          attrs: { disabled: true },
        },
      ];
      data.forEach((policy: Policy) => {
        policyOptions.value.push({
          label: policy.spec.displayName,
          value: policy.metadata.name,
          attrs: {},
        });
      });
    }
  } catch (error) {
    console.error(error);
  }
  isFetchingPolicies.value = false;
};

const changeNextTokenAndObject = () => {
  s3Objects.value.currentToken = s3Objects.value.nextToken;
  s3Objects.value.currentContinuationObject =
    s3Objects.value.nextContinuationObject;
  s3Objects.value.nextToken = "";
  s3Objects.value.nextContinuationObject = "";
};

const clearTokenAndObject = () => {
  s3Objects.value.currentToken = "";
  s3Objects.value.currentContinuationObject = "";
  s3Objects.value.nextToken = "";
  s3Objects.value.nextContinuationObject = "";
};

// filePrefix will not be updated from user input
// if you want to update filePrefix, please call `handleFirstPage`
const fetchObjects = async () => {
  if (!policyName.value) {
    return;
  }
  isFetching.value = true;
  s3Objects.value.objects = [];
  try {
    const { status, data } = await s3LinkControllerApi.listObjects({
      policyName: policyName.value,
      pageSize: size.value,
      continuationToken: s3Objects.value.currentToken,
      continuationObject: s3Objects.value.currentContinuationObject,
      unlinked: selectedLinkedStatusItem.value,
      filePrefix: filePrefix.value,
    });
    if (status === 200) {
      s3Objects.value = data;
      if (
        s3Objects.value.objects?.length === 0 &&
        s3Objects.value.hasMore &&
        s3Objects.value.nextToken
      ) {
        changeNextTokenAndObject();
        await fetchObjects();
      } else if (
        s3Objects.value.objects?.length === 0 &&
        !s3Objects.value.hasMore &&
        page.value > 1
      ) {
        page.value = 1;
        clearTokenAndObject();
        await fetchObjects();
        Toast.warning("最后一页为空，已返回第一页");
      }
    }
  } catch (error) {
    console.error(error);
  }
  selectedFiles.value.length = 0;
  checkedAll.value = false;
  isFetching.value = false;
};

const checkSelection = (file: ObjectVo) => {
  return selectedFiles.value.includes(file.key || "");
};

const handleLink = async () => {
  isLinking.value = true;
  isShowModal.value = true;
  linkTips.value = `正在关联${selectedFiles.value.length}个文件`;
  linkFailedTable.value = [];
  const linkResult = await s3LinkControllerApi.addAttachmentRecord({
    linkRequest: {
      policyName: policyName.value,
      objectKeys: selectedFiles.value,
      groupName: selectedGroup.value,
    },
  });
  const items = linkResult.data.items || [];
  const successCount = items.filter((item) => item.success).length;
  const failedCount = items.filter((item) => !item.success).length;
  linkTips.value = `关联成功${successCount}个文件，关联失败${failedCount}个文件`;

  if (failedCount > 0) {
    linkFailedTable.value = items.filter((item) => !item.success);
  }
  isLinking.value = false;
};

const selectOneAndLink = (file: ObjectVo) => {
  selectedFiles.value = [file.key || ""];
  handleLink();
};

const handleNextPage = () => {
  if (!policyName.value || !s3Objects.value.hasMore) {
    return;
  }
  isFetching.value = true;
  page.value += 1;
  changeNextTokenAndObject();
  fetchObjects();
};

const handleFirstPage = () => {
  if (!policyName.value) {
    return;
  }
  isFetching.value = true;
  page.value = 1;
  clearTokenAndObject();
  filePrefix.value = filePrefixBind.value;
  fetchObjects();
};

const handleModalClose = () => {
  isShowModal.value = false;
  fetchObjects();
};

const fetchCustomGroups = async () => {
  const { status, data } = await coreApiClient.storage.group.listGroup({
    labelSelector: ["!halo.run/hidden"],
    sort: ["metadata.creationTimestamp,asc"],
  });
  if (status === 200) {
    customGroups.value = data.items;
  }
};

onMounted(() => {
  fetchPolicies();
  fetchCustomGroups();
});

watch(selectedFiles, (newValue) => {
  checkedAll.value =
    s3Objects.value.objects
      ?.filter((file) => !file.isLinked)
      .filter((file) => !newValue.includes(file.key || "")).length === 0 &&
    s3Objects.value.objects?.length !== 0;
});

watch(selectedLinkedStatusItem, handleFirstPage);
</script>

<template>
  <VPageHeader title="关联S3文件">
    <template #icon>
      <CarbonFolderDetailsReference class="mr-2 self-center" />
    </template>
  </VPageHeader>
  <div class="m-0 md:m-4">
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class="block w-full bg-gray-50 px-4 py-3">
          <div
            class="relative flex flex-col flex-wrap items-start gap-4 sm:flex-row sm:items-center"
          >
            <div class="hidden items-center sm:flex">
              <input
                v-model="checkedAll"
                type="checkbox"
                @change="handleCheckAllChange"
              />
            </div>
            <div class="flex w-full flex-1 items-center sm:w-auto">
              <div
                v-if="!selectedFiles.length"
                class="flex flex-wrap items-center gap-2"
              >
                <span class="whitespace-nowrap">存储策略:</span>
                <FormKit
                  id="policyChoose"
                  outer-class="!p-0"
                  style="min-width: 10rem"
                  v-model="policyName"
                  name="policyName"
                  type="select"
                  :options="policyOptions"
                  @change="handleFirstPage"
                ></FormKit>
                <icon-error-warning v-if="!policyName" class="text-red-500" />
                <SearchInput
                  v-model="filePrefixBind"
                  v-if="policyName"
                  placeholder="请输入文件名前缀搜索"
                  @update:modelValue="handleFirstPage"
                ></SearchInput>
              </div>
              <VSpace v-else>
                <VButton type="primary" @click="handleLink"> 关联 </VButton>
              </VSpace>
            </div>
            <VSpace spacing="lg" class="flex-wrap">
              <FilterCleanButton
                v-if="selectedLinkedStatusItem != linkedStatusItems[0].value"
                @click="selectedLinkedStatusItem = linkedStatusItems[0].value"
              />
              <FilterDropdown
                v-model="selectedLinkedStatusItem"
                :label="$t('core.common.filters.labels.status')"
                :items="linkedStatusItems"
              />

              <div class="flex flex-row gap-2">
                <div
                  class="group cursor-pointer rounded p-1 hover:bg-gray-200"
                  @click="fetchObjects()"
                >
                  <IconRefreshLine
                    v-tooltip="$t('core.common.buttons.refresh')"
                    :class="{
                      'animate-spin text-gray-900': isFetching,
                    }"
                    class="h-4 w-4 text-gray-600 group-hover:text-gray-900"
                  />
                </div>
              </div>
            </VSpace>
          </div>
        </div>
      </template>

      <VLoading v-if="isFetching" />

      <Transition v-else-if="!s3Objects.objects?.length" appear name="fade">
        <VEmpty message="空空如也" :title="emptyTips"> </VEmpty>
      </Transition>

      <Transition v-else appear name="fade">
        <div class="box-border h-full w-full">
          <div style="padding: 0.5rem 1rem 0">
            <span class="ml-1 mb-1 block text-sm text-gray-500">
              关联后所加入的分组
            </span>
            <div
              class="mb-5 grid grid-cols-2 gap-x-2 gap-y-3 md:grid-cols-3 lg:grid-cols-4 2xl:grid-cols-6"
            >
              <button
                type="button"
                class="inline-flex h-full w-full items-center gap-2 rounded-md border border-gray-200 bg-white px-3 py-2.5 text-sm font-medium text-gray-800 hover:bg-gray-50 hover:shadow-sm"
                v-for="(group, index) in [defaultGroup, ...customGroups]"
                :key="index"
                :class="{
                  '!bg-gray-100 shadow-sm':
                    group.metadata.name === selectedGroup,
                }"
                @click="selectedGroup = group.metadata.name"
              >
                <div
                  class="inline-flex w-full flex-1 gap-x-2 break-all text-left"
                >
                  <slot name="text">
                    {{ group?.spec.displayName }}
                  </slot>
                  <VStatusDot
                    v-if="group?.metadata.deletionTimestamp"
                    v-tooltip="$t('core.common.status.deleting')"
                    state="warning"
                    animate
                  />
                </div>
                <div class="flex-none">
                  <IconCheckboxCircle
                    v-if="group.metadata.name === selectedGroup"
                    class="text-primary"
                  />
                </div>
              </button>
            </div>
          </div>
          <VEntityContainer>
            <VEntity
              v-for="(file, index) in s3Objects.objects"
              :key="index"
              :is-selected="checkSelection(file)"
            >
              <template #checkbox>
                <input
                  v-model="selectedFiles"
                  :value="file.key || ''"
                  :disabled="file.isLinked"
                  type="checkbox"
                />
              </template>
              <template #start>
                <VEntityField>
                  <template #description>
                    <AttachmentFileTypeIcon
                      :display-ext="false"
                      :file-name="file.displayName || ''"
                      :width="8"
                      :height="8"
                    />
                  </template>
                </VEntityField>
                <VEntityField
                  :title="file.displayName || ''"
                  :description="file.key || ''"
                />
              </template>
              <template #end>
                <VEntityField>
                  <template #description>
                    <VTag :theme="file.isLinked ? 'default' : 'primary'">
                      {{ file.isLinked ? "已关联" : "未关联" }}
                    </VTag>
                  </template>
                </VEntityField>
                <VEntityField>
                  <template #description>
                    <VButton
                      :disabled="file.isLinked || false"
                      @click="selectOneAndLink(file)"
                    >
                      关联
                    </VButton>
                  </template>
                </VEntityField>
              </template>
            </VEntity>
          </VEntityContainer>
        </div>
      </Transition>

      <template #footer>
        <div class="bg-white sm:flex sm:items-center justify-between">
          <div class="inline-flex items-center gap-5">
            <span class="text-xs text-gray-500 hidden md:flex"
              >共 {{ s3Objects.objects?.length }} 项数据</span
            >
            <span class="text-xs text-gray-500 hidden md:flex"
              >已自动过滤文件夹对象，页面实际显示数量少为正常现象</span
            >
          </div>
          <div class="inline-flex items-center gap-5">
            <div class="inline-flex items-center gap-2">
              <VButton @click="handleFirstPage" :disabled="!policyName"
                >返回第一页</VButton
              >

              <span class="text-sm text-gray-500">第 {{ page }} 页</span>

              <VButton
                @click="handleNextPage"
                :disabled="!s3Objects.hasMore || isFetching || !policyName"
              >
                下一页
              </VButton>
            </div>
            <div class="inline-flex items-center gap-2">
              <select
                v-model="size"
                class="h-8 border outline-none rounded-base pr-10 border-solid px-2 text-gray-800 text-sm border-gray-300 page-size-select"
                @change="handleFirstPage"
              >
                <option
                  v-for="(sizeOption, index) in [20, 50, 100, 200]"
                  :key="index"
                  :value="sizeOption"
                >
                  {{ sizeOption }}
                </option>
              </select>
              <span class="text-sm text-gray-500">条/页</span>
            </div>
          </div>
        </div>
      </template>
    </VCard>
  </div>
  <VModal
    :visible="isShowModal"
    :fullscreen="false"
    :title="'关联结果'"
    :width="500"
    :mount-to-body="true"
    @close="handleModalClose"
  >
    <template #footer>
      <VSpace>
        <VButton :loading="isLinking" type="primary" @click="handleModalClose">
          确定
        </VButton>
      </VSpace>
    </template>
    <div class="flex flex-col">
      {{ linkTips }}
      <table v-if="linkFailedTable.length != 0">
        <tr>
          <th class="border border-black font-normal">失败对象</th>
          <th class="border border-black font-normal">失败原因</th>
        </tr>
        <tr v-for="failedInfo in linkFailedTable" :key="failedInfo.objectKey">
          <th class="border border-black font-normal">
            {{ failedInfo.objectKey }}
          </th>
          <th class="border border-black font-normal">
            {{ failedInfo.message }}
          </th>
        </tr>
      </table>
    </div>
  </VModal>
</template>

<style lang="scss" scoped>
.page-size-select:focus {
  --tw-border-opacity: 1;
  border-color: rgba(var(--colors-primary), var(--tw-border-opacity));
}
</style>
