<script setup lang="ts">
import {
  IconRefreshLine,
  Toast,
  VButton,
  VCard,
  VEmpty,
  VEntity,
  VEntityField,
  VLoading,
  VModal,
  VPageHeader,
  VSpace,
  VTag,
} from "@halo-dev/components";
import CarbonFolderDetailsReference from "~icons/carbon/folder-details-reference";
import {computed, onMounted, ref, watch} from "vue";
import {
  getApisS3OsHaloRunV1Alpha1ObjectsByPolicyName,
  getApisS3OsHaloRunV1Alpha1PoliciesS3,
  postApisS3OsHaloRunV1Alpha1AttachmentsLink,
} from "@/controller";
import type {ObjectVo, S3ListResult, Policy, LinkResultItem} from "@/interface";

const selectedFiles = ref<string[]>([]);
const policyName = ref<string>("");
const page = ref(1);
const size = ref(50);
const policyOptions = ref<{ label: string; value: string; attrs: any }[]>([{
  label: "请选择存储策略",
  value: "",
  attrs: {disabled: true}
}]);
const s3Objects = ref<S3ListResult>({
  objects: [],
  hasMore: false,
  currentToken: "",
  nextToken: "",
  currentContinuationObject: "",
  nextContinuationObject: "",
});
// view state
const isFetching = ref(false);
const isShowModal = ref(false);
const isLinking = ref(false);
const isFetchingPolicies = ref(true);

const linkTips = ref("");
const linkFailedTable = ref<LinkResultItem[]>([]);
const linkedStatusItems: { label: string; value?: boolean }[] = [
  {label: "全部"},
  {label: "未关联", value: true},
];

// action state
const checkedAll = ref(false);
const selectedLinkedStatusItem = ref<boolean | undefined>(linkedStatusItems[0].value);

const emptyTips = computed(() => {
  if (isFetchingPolicies.value) {
    return "正在加载存储策略";
  } else {
    if (policyOptions.value.length <= 1) {
      return "没有可用的存储策略，请前往【附件】添加S3存储策略";
    } else {
      if (!policyName.value) {
        return "请在左上方选择存储策略";
      } else {
        return "该存储策略的 桶/文件夹 下没有文件";
      }
    }
  }
});

const handleCheckAllChange = (e: Event) => {
  const {checked} = e.target as HTMLInputElement;

  if (checked) {
    selectedFiles.value =
      s3Objects.value.objects?.filter(file => !file.isLinked).map((file) => {
        return file.key || "";
      }) || [];
  } else {
    selectedFiles.value.length = 0;
    checkedAll.value = false;
  }
};


const fetchPolicies = async () => {
  try {
    const policiesData = await getApisS3OsHaloRunV1Alpha1PoliciesS3();
    if (policiesData.status == 200) {
      policyOptions.value = [{
        label: "请选择存储策略",
        value: "",
        attrs: {disabled: true}
      }];
      policiesData.data.forEach((policy: Policy) => {
        policyOptions.value.push({
          label: policy.spec.displayName,
          value: policy.metadata.name,
          attrs: {}
        });
      });
    }
  } catch (error) {
    console.error(error);
  }
  isFetchingPolicies.value = false;
};


onMounted(() => {
  fetchPolicies();
});

watch(selectedFiles, (newValue) => {
  checkedAll.value = s3Objects.value.objects?.filter(file => !file.isLinked)
      .filter(file => !newValue.includes(file.key || "")).length == 0
    && s3Objects.value.objects?.length != 0;
});

watch(selectedLinkedStatusItem, () => {
  handleFirstPage();
});

const changeNextTokenAndObject = () => {
  s3Objects.value.currentToken = s3Objects.value.nextToken;
  s3Objects.value.currentContinuationObject = s3Objects.value.nextContinuationObject;
  s3Objects.value.nextToken = "";
  s3Objects.value.nextContinuationObject = "";
};

const clearTokenAndObject = () => {
  s3Objects.value.currentToken = "";
  s3Objects.value.currentContinuationObject = "";
  s3Objects.value.nextToken = "";
  s3Objects.value.nextContinuationObject = "";
};

const fetchObjects = async () => {
  if (!policyName.value) {
    return;
  }
  isFetching.value = true;
  s3Objects.value.objects = [];
  try {
    const objectsData = await getApisS3OsHaloRunV1Alpha1ObjectsByPolicyName({
      policyName: policyName.value,
      pageSize: size.value,
      continuationToken: s3Objects.value.currentToken,
      continuationObject: s3Objects.value.currentContinuationObject,
      unlinked: selectedLinkedStatusItem.value,
    });
    if (objectsData.status == 200) {
      s3Objects.value = objectsData.data;

      if (s3Objects.value.objects?.length == 0 && s3Objects.value.hasMore && s3Objects.value.nextToken) {
        changeNextTokenAndObject();
        await fetchObjects();
      } else if (s3Objects.value.objects?.length == 0 && !s3Objects.value.hasMore && page.value > 1) {
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
  const linkResult = await postApisS3OsHaloRunV1Alpha1AttachmentsLink({
    policyName: policyName.value,
    objectKeys: selectedFiles.value
  });

  const successCount = linkResult.data.items.filter(item => item.success).length;
  const failedCount = linkResult.data.items.filter(item => !item.success).length;
  linkTips.value = `关联成功${successCount}个文件，关联失败${failedCount}个文件`;

  if (failedCount > 0) {
    linkFailedTable.value = linkResult.data.items.filter(item => !item.success);
  }
  isLinking.value = false;
};

const selectOneAndLink = (file: ObjectVo) => {
  selectedFiles.value = [file.key || ""];
  handleLink();
};

const handleNextPage = () => {
  if (!policyName.value) {
    return;
  }
  if (s3Objects.value.hasMore) {
    isFetching.value = true;
    page.value += 1;
    changeNextTokenAndObject();
    fetchObjects();
  }
};

const handleFirstPage = () => {
  if (!policyName.value) {
    return;
  }
  isFetching.value = true;
  page.value = 1;
  clearTokenAndObject();
  fetchObjects();
};

const handleModalClose = () => {
  isShowModal.value = false;
  fetchObjects();
};
</script>

<template>
  <VPageHeader title="关联S3文件(Beta)">
    <template #icon>
      <CarbonFolderDetailsReference class="mr-2 self-center"/>
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
                class="h-4 w-4 rounded border-gray-300 text-indigo-600"
                type="checkbox"
                @change="handleCheckAllChange"
              />
            </div>
            <div class="flex w-full flex-1 items-center sm:w-auto">
              <div
                v-if="!selectedFiles.length"
                class="flex items-center gap-2"
              >
                <span>存储策略:</span>
                <FormKit
                  id="policyChoose"
                  outer-class="!p-0"
                  style="min-width: 10rem;"
                  v-model="policyName"
                  name="policyName"
                  type="select"
                  :options="policyOptions"
                  @change="fetchObjects()"
                ></FormKit>
              </div>
              <VSpace v-else>
                <VButton type="primary" @click="handleLink">
                  关联
                </VButton>
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

      <VLoading v-if="isFetching"/>

      <Transition v-else-if="!s3Objects.objects?.length" appear name="fade">
        <VEmpty
          message="空空如也"
          :title="emptyTips"
        >
        </VEmpty>
      </Transition>

      <Transition v-else appear name="fade">
        <ul
          class="box-border h-full w-full divide-y divide-gray-100"
          role="list"
        >
          <li v-for="(file, index) in s3Objects.objects" :key="index">
            <VEntity :is-selected="checkSelection(file)">
              <template
                #checkbox
              >
                <input
                  v-model="selectedFiles"
                  :value="file.key || ''"
                  class="h-4 w-4 rounded border-gray-300 text-indigo-600"
                  name="post-checkbox"
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
                    <VTag :theme="file.isLinked ? 'default':'primary'">
                      {{
                        file.isLinked ? '已关联' : '未关联'
                      }}
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
          </li>
        </ul>
      </Transition>

      <template #footer>
        <div class="bg-white sm:flex sm:items-center justify-between">
          <div class="inline-flex items-center gap-5">
            <span class="text-xs text-gray-500 hidden md:flex">共 {{ s3Objects.objects?.length }} 项数据</span>
            <span class="text-xs text-gray-500 hidden md:flex">已自动过滤文件夹对象，页面实际显示数量少为正常现象</span>
          </div>
          <div class="inline-flex items-center gap-5">
            <div class="inline-flex items-center gap-2">
              <VButton size="small" @click="handleFirstPage" :disabled="!policyName">返回第一页</VButton>

              <span class="text-sm text-gray-500">第 {{ page }} 页</span>

              <VButton size="small" @click="handleNextPage" :disabled="!s3Objects.hasMore || isFetching || !policyName">
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
        <VButton
          :loading="isLinking"
          type="primary"
          @click="handleModalClose"
        >
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
          <th class="border border-black font-normal">{{failedInfo.objectKey}}</th>
          <th class="border border-black font-normal">{{failedInfo.message}}</th>
        </tr>
      </table>
    </div>
  </VModal>
</template>

<style lang="scss" scoped>
.page-size-select:focus {
  --tw-border-opacity: 1;
  border-color: rgba(var(--colors-primary),var(--tw-border-opacity));
}
</style>
