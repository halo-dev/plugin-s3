export interface AttachmentStatus {
    /**
     * Permalink of attachment.
     * If it is in local storage, the public URL will be set.
     * If it is in s3 storage, the Object URL will be set.
     *
     */
    permalink?: any;
}
