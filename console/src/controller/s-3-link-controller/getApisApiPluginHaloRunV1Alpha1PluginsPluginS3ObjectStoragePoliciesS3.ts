import request from "@/utils/request";
import { Policy, DeepRequired } from "../../interface";

/**
 * /apis/api.plugin.halo.run/v1alpha1/plugins/PluginS3ObjectStorage/policies/s3
 */
export function getApisApiPluginHaloRunV1Alpha1PluginsPluginS3ObjectStoragePoliciesS3() {
    return request.get<DeepRequired<Policy[]>>(`/apis/api.plugin.halo.run/v1alpha1/plugins/PluginS3ObjectStorage/policies/s3`);
}
