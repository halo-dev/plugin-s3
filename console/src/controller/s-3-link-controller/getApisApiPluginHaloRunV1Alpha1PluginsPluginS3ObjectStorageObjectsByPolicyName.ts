import request from "@/utils/request";
import { S3ListResult, DeepRequired } from "../../interface";

/**
 * /apis/api.plugin.halo.run/v1alpha1/plugins/PluginS3ObjectStorage/objects/{policyName}
 */
export function getApisApiPluginHaloRunV1Alpha1PluginsPluginS3ObjectStorageObjectsByPolicyName(params: GetApisApiPluginHaloRunV1Alpha1PluginsPluginS3ObjectStorageObjectsByPolicyNameParams) {
    const paramsInput = {
        continuationToken: params.continuationToken,
        continuationObject: params.continuationObject,
        pageSize: params.pageSize,
        unlinked: params.unlinked,
    };
    return request.get<DeepRequired<S3ListResult>>(`/apis/api.plugin.halo.run/v1alpha1/plugins/PluginS3ObjectStorage/objects/${params.policyName}`, {
        params: paramsInput,
    });
}

interface GetApisApiPluginHaloRunV1Alpha1PluginsPluginS3ObjectStorageObjectsByPolicyNameParams {
    policyName: string;
    continuationToken?: string;
    continuationObject?: string;
    pageSize: number;
    unlinked?: boolean;
}
