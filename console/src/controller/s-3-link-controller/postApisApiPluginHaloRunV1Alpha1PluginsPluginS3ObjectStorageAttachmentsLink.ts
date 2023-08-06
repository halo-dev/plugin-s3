import request from "@/utils/request";
import { LinkResult, DeepRequired, LinkRequest } from "../../interface";

/**
 * /apis/api.plugin.halo.run/v1alpha1/plugins/PluginS3ObjectStorage/attachments/link
 */
export function postApisApiPluginHaloRunV1Alpha1PluginsPluginS3ObjectStorageAttachmentsLink(input: LinkRequest) {
    return request.post<DeepRequired<LinkResult>>(`/apis/api.plugin.halo.run/v1alpha1/plugins/PluginS3ObjectStorage/attachments/link`, input);
}
