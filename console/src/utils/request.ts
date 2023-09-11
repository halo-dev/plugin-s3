import axios from "axios";
import {Toast} from "@halo-dev/components";

const baseURL = import.meta.env.VITE_API_URL;
const request = axios.create({
  baseURL,
  withCredentials: true,
});

// 非200状态码就弹窗
request.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error) => {
    const errorResponse = error.response;
    if (!errorResponse) {
      return Promise.reject(error);
    }
    const { status } = errorResponse;
    if (status !== 200) {
      Toast.error("status: " + status);
    }
    return Promise.reject(error);
  }
);

request.defaults.headers.common["X-Requested-With"] = "XMLHttpRequest";
// TODO 使用halo console 中的axios https://github.com/halo-dev/halo/issues/3979
export default request;

