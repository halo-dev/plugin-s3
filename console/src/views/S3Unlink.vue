<script setup lang="ts">
import {Dialog, Toast, VDropdownDivider, VDropdownItem} from "@halo-dev/components";
import {useQueryClient} from "@tanstack/vue-query";
import {axiosInstance} from "@halo-dev/api-client"
import type {Attachment} from "@halo-dev/api-client";
import {S3UnlinkControllerApi} from "@/api"

const s3UnlinkControllerApi = new S3UnlinkControllerApi(undefined, axiosInstance.defaults.baseURL, axiosInstance);

const props = defineProps<{
  attachment: Attachment;
}>();
const queryClient = useQueryClient();

const handleUnlink = () => {
  Dialog.warning({
    title: "解除 S3 关联",
    description: "解除关联后，附件中的记录将会被删除，而对象存储中的文件仍然保留，若需重新关联请使用“关联 S3 文件”功能。",
    confirmType: "danger",
    confirmText: "确定",
    cancelText: "取消",
    onConfirm: async () => {
      try {
        await s3UnlinkControllerApi.unlink({name: props.attachment.metadata.name});
        Toast.success("解除关联成功");
      } catch (e) {
        console.error("Failed to delete attachment", e);
      } finally {
        await queryClient.invalidateQueries({queryKey: ["attachments"]});
      }
    },
  });
}
</script>
<template>
  <div>
    <VDropdownDivider/>
    <VDropdownItem type="danger" @click="handleUnlink">解除 S3 关联</VDropdownItem>
  </div>
</template>
