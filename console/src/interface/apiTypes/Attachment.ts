import { Metadata, AttachmentSpec, AttachmentStatus } from "../../interface";

export interface Attachment {
    apiVersion: any;
    kind: any;
    metadata: Metadata;
    spec: AttachmentSpec;
    status?: AttachmentStatus;
}
