# Booking layout aligned with prototype

Reference: docs/web-demo/prototype/event-detail.html. The live page adopts the auditorium panel, stage strip, compact square seats, VIP borders, selected/held/sold styling and visual legend. Standing tiers use quantity steppers; the selection summary sits beside the map. For standing-only events, ticket tiers and the summary fill the layout without an empty map panel. Mobile layouts stack and allow horizontal seat-row scrolling.

Merged origin/dev 1993e4f's seat quantity selection. The map remains visible before choosing quantity; reserving a seated quantity contributes to the shared eight-ticket limit before individual seats are picked. Submission requires the requested seats to be selected and rejects fractional/invalid quantities. Actual availability, prices, seat IDs and hold/payment operations remain server-backed. No prototype sample seats, preset selections or sample prices were copied.

Verification: 35 booking client tests pass, including mixed standing/seated quota and sidebar controls. Full JavaScript suite: 195 tests, 182 passed, 13 environment-dependent tests skipped, zero failures. Full local build passed; JSP compilation completed with zero errors. Live HTTP returns the auditorium, standing and summary panels and the updated booking script at port 18082. Existing untracked database work and private configuration/credentials were preserved.

The browser tool's local URL restriction remains in effect, so visual screenshots and exact pixel alignment were not verified in this run. The checks cover source structure, served assets and tested client behavior.
