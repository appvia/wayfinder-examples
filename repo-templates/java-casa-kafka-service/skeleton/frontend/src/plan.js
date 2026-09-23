import { Plan } from "@dwp/govuk-casa";

// The whole journey is one page.
export const WAYPOINT = "message";

export default function planFactory() {
  const plan = new Plan({ arbiter: "auto" });
  // A plan is a graph of routes, so one page needs a route out of it to exist
  // at all. Sending the message redirects back to the page before this route
  // is followed.
  plan.setRoute(WAYPOINT, "url:///");
  return plan;
}
