import { createCookies, useCookies as cookies } from "@vueuse/integrations/useCookies";
import { useRequest, useResponse } from "~/composables/useResReq";
import * as cookie from "cookie";

export const useCookies = () => {
  if (import.meta.env.SSR) {
    const req = useRequest();
    const res = useResponse();
    if (!req || !req.headers) {
      console.error("req null?!");
      console.trace();
    }
    const cookies = createCookies(req)();
    cookies.addChangeListener((change) => {
      if (!res || res.headersSent) {
        return;
      }

      if (change.value === undefined) {
        res.setHeader("Set-Cookie", change.name + "=");
      } else {
        res.setHeader("Set-Cookie", cookie.serialize(change.name, change.value, change.options));
      }
    });
    return cookies;
  } else {
    return cookies();
  }
};
