import { field, validators as r, endSession } from "@dwp/govuk-casa";

import { WAYPOINT } from "./plan.js";
import { getMessage, submitMessage } from "./message-client.js";

export const MAX_LENGTH = 500;

/**
 * Before the page renders, reads the status of the last message this session
 * sent, so the page can show whether it has been through Kafka yet.
 */
function showLastMessage({ messageServiceUrl, log }) {
  return async (req, res, next) => {
    const reference = req.session.lastReference;
    if (!reference) {
      return next();
    }
    res.locals.lastReference = reference;
    try {
      res.locals.lastStatus = (await getMessage(messageServiceUrl, reference)).status;
    } catch (err) {
      log.warn(`could not read status of message ${reference}: ${err.message}`);
    }
    return next();
  };
}

/**
 * Sends the message once it passes validation. On 201 the answer is thrown
 * away so the form is empty again, and only the reference is kept.
 */
function sendOnSubmit({ messageServiceUrl, log }) {
  return async (req, res, next) => {
    const { message } = req.casa.journeyContext.getDataForPage(WAYPOINT) ?? {};
    let created;
    try {
      created = await submitMessage(messageServiceUrl, message);
    } catch (err) {
      log.error(`sending the message failed: ${err.message}`);
      return res.status(502).render("submission-error.njk");
    }

    log.info(`message sent: ${created.reference}`);
    return endSession(req, (err) => {
      if (err) {
        return next(err);
      }
      req.session.lastReference = created.reference;
      return req.session.save((saveErr) =>
        saveErr ? next(saveErr) : res.redirect(302, `${req.baseUrl}/${WAYPOINT}`),
      );
    });
  };
}

export default function pagesFactory({ messageServiceUrl, log }) {
  return [
    {
      waypoint: WAYPOINT,
      view: "pages/message.njk",
      fields: [
        field("message")
          .processor((value) => String(value ?? "").trim())
          .validators([
            r.required.make({ errorMsg: "Enter a message" }),
            r.strlen.make({
              max: MAX_LENGTH,
              errorMsgMax: `Message must be ${MAX_LENGTH} characters or fewer`,
            }),
          ]),
      ],
      hooks: [
        { hook: "prerender", middleware: showLastMessage({ messageServiceUrl, log }) },
        { hook: "preredirect", middleware: sendOnSubmit({ messageServiceUrl, log }) },
      ],
    },
  ];
}
