export interface PolicySpec {
    /** Reference name of ConfigMap extension */
    configMapName?: string;
    /** Display name of policy */
    displayName: string;
    /** Reference name of PolicyTemplate */
    templateName: string;
}
