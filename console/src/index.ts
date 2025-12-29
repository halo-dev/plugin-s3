import { definePlugin } from "@halo-dev/ui-shared";
import "uno.css";
import type { Ref } from "vue";
import { defineAsyncComponent, markRaw } from "vue";
import CarbonFolderDetailsReference from "~icons/carbon/folder-details-reference";

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: "ToolsRoot",
      route: {
        path: "s3-link",
        name: "S3Link",
        component: () => import("./views/S3Link.vue"),
        meta: {
          title: "S3 关联",
          description: "提供将 S3 存储桶中的文件关联到 Halo 中的功能。",
          searchable: true,
          permissions: ["plugin:s3os:link"],
          menu: {
            name: "S3 关联",
            icon: markRaw(CarbonFolderDetailsReference),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {
    "attachment:list-item:operation:create": (attachment: Ref) => {
      return [
        {
          priority: 21,
          component: defineAsyncComponent(() => import("./views/S3Unlink.vue")),
          permissions: ["plugin:s3os:unlink"],
          props: {
            attachment: attachment,
          },
          hidden: !(
            attachment.value.metadata.annotations &&
            attachment.value.metadata.annotations["s3os.plugin.halo.run/object-key"]
          ),
        },
      ];
    },
  },
});
