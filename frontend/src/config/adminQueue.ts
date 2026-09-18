/**
 * How long a reservation of the shared admin tray lasts before the platform takes it back, in
 * minutes (#100).
 *
 * **The frontend never enforces it** — the backend's scheduled job is what actually releases a stale
 * claim. This constant exists so that the screen and the help say the same number out loud: an admin
 * who takes an item and walks away has to be told what happens next, and "fifteen minutes" written by
 * hand in four places in two languages is how that stops being true the first time it changes.
 *
 * **In `config/` and not in `features/adminQueue/`, for the reason §3.1.2 gives**: a second feature
 * needs it. The tray screen interpolates it into its hint and `features/help` interpolates it into
 * the section that explains the reservation — and a feature never imports another (§3.1.5), so the
 * one number they share lives in the transversal layer, next to `pageSize` and the `staleTime`
 * policy, which are the same kind of thing: a decision about how the platform behaves, written once.
 *
 * The backend's limit is configurable (`app.admin-queue.claim-timeout`, default `PT15M`) and **F3.5
 * moves it to `system_settings`** (#141). When it stops being a constant there it stops being one
 * here too: it arrives with the tray's response, and this file is where that change lands.
 */
export const CLAIM_TIMEOUT_MINUTES = 15
