import request from "@/utils/request";
import { DeepRequired } from "../../interface";
import { Attachment } from "@halo-dev/api-client";

/**
 * /apis/s3os.halo.run/v1alpha1/attachments/{name}
 */
export function deleteApisS3OsHaloRunV1Alpha1AttachmentsByName(params: DeleteApisS3OsHaloRunV1Alpha1AttachmentsByNameParams) {
    return request.delete<DeepRequired<Attachment>>(`/apis/s3os.halo.run/v1alpha1/attachments/${params.name}`);
}

interface DeleteApisS3OsHaloRunV1Alpha1AttachmentsByNameParams {
    name: any;
}
