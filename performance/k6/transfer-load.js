import http from "k6/http";
import { check } from "k6";

export const options = {
  vus: 10,
  duration: "30s",
};

export default function () {
  const baseUrl = __ENV.LEDGER_BASE_URL || "http://localhost:5001";
  const response = http.get(`${baseUrl}/`);
  check(response, {
    "ledger responds": (r) => r.status < 500,
  });
}
