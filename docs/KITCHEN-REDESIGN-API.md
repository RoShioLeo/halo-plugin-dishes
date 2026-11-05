# Shared kitchen contract

This is the new, unpublished kitchen model. It has no migration or compatibility
API. Quantities are nonnegative safe integers in proposals and positive integers
in stored item rows. Zero removes an item only when its corresponding save or
submission succeeds.

## Public ordering

- One date and meal shares one order, with per-person sources and submitted batches.
- Dates: today through today + 7, inclusive, Asia/Shanghai. Breakfast before 10:00,
  lunch from 10:00 through 15:59, dinner from 16:00. An explicit link/manual choice wins.
- Guests choose a nickname; account names/avatars come from Halo. The server owns
  identity and source ownership. Opaque participant IDs reveal no guest secret.
- Own pending selections, wishes and meal remarks save automatically. Other people's
  pending rows allow only quantity changes. No public caller may change submitted
  rows; chef changes use the kitchen workspace.
- Submission locks the entire active batch. New choices enter a new active batch.
- Off-menu wishes and remarks remain text, never automatically arranged dishes.
- Catalogue quantities: 1–99; zero removes. Quantity controls step by one portion.
- Category and custom-tag filters are single choice. Meal tags match the chosen
  meal automatically. No meal tags means all day; selecting all three meal tags
  normalizes to the same representation while preserving custom tags. Sold-out dishes stay visible,
  sort last and show 售罄 at the action position; they cannot be added/increased.

Public base: `/plugins/dishes/public`.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/viewer` | Current identity, permissions and allowed dates |
| GET | `/csrf-metadata` | Current session CSRF header/token |
| GET | `/dishes` | Catalogue and tags |
| GET | `/order?date=YYYY-MM-DD&period=lunch` | Shared sources and published menu |
| POST | `/cart` | Own pending save or peer quantity-only save |
| POST | `/submit` | Submit/lock the active shared batch |
| GET | `/daily-menu?date=YYYY-MM-DD` | Nonempty confirmed meal snapshots |
| GET | `/access/status` | Optional family access status |
| POST | `/access/password-verify` | Optional family access password |

Cart body:
```json
{
  "date": "2026-11-05", "period": "lunch", "batchId": "active-batch-id",
  "revision": 0, "nickname": "访客", "targetParticipantId": null,
  "items": [{ "dishId": 1, "quantity": 2, "note": "" }],
  "offMenu": "", "remark": ""
}
```

Submit body:
```json
{
  "date": "2026-11-05", "period": "lunch", "nickname": "访客",
  "batchId": "active-batch-id", "draftContributionVersions": { "source-id": 1 }
}
```

Return envelopes are `{ok:true,data:...}` or `{ok:false,code,message}`. Authentication,
conflict, validation and missing-resource errors use the corresponding HTTP status.
All browser writes use the header returned by `/csrf-metadata` and session cookies.
Public access never grants raw aggregate-resource permissions.

## Kitchen workspace

Admin base: `/apis/plugins/dishes/admin`.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/kitchen?date=YYYY-MM-DD` | All three meals for a date |
| POST | `/kitchen/commit` | Atomic staged adjustment, batch lock and publication |
| GET/POST | `/categories` | Category listing/creation |
| PUT/DELETE | `/categories/{id}` | Category update/removal |
| GET/POST | `/dishes` | Catalogue listing/creation |
| PUT/DELETE | `/dishes/{id}` | Full dish update/removal |
| GET/PUT | `/settings` | Current branding and access settings |

The workspace displays one selected meal. Its two regions are source records and
final menu. Choose one side to edit; the other side synchronizes read-only. Submit
or cancel before changing editing sides. No source changes are written before commit.
User wishes, remarks and original source metadata remain read-only.
New submitted choices not included in the published snapshot show one 待确认 badge;
unsubmitted choices remain 待提交. Confirming the menu clears the pending badge.

- Source mode: change a person's item quantity, then recompute the menu total.
- Menu mode: recompute allocations from the frozen source baseline on every change.
  Extra portions belong to the current chef's active source. Deductions consume
  sources from the end of the server's visible order. `6 -> 4 -> 6` restores ownership.
- Keep zero rows until commit succeeds. Source-only wishes/remarks remain visible
  after their food items are removed; records without any content disappear.
- Keep one submit action. Refreshing or switching meals must not overwrite staged
  input. Conflicts preserve the draft and require explicit reload, never blind retry.

Each meal includes `stateRevision`, `activeBatchId`, all `contributionVersions`,
ordered `participants`, `workingItems`, `chefParticipantId`, chef display data and
its separate published `menu`. The server's source order is authoritative.

Commit body:
```json
{
  "date": "2026-11-05", "period": "lunch", "batchId": "active-batch-id",
  "stateRevision": 1, "revision": 0, "contributionVersions": { "source-id": 1 },
  "mode": "requests",
  "sourceQuantities": [{
    "participantId": "source-id", "items": [{ "key": "dish:1", "quantity": 2 }]
  }],
  "items": [{ "dishId": 1, "customId": null, "name": "一道菜", "quantity": 2, "imageUrl": "", "note": "" }],
  "note": ""
}
```

`mode` is `requests` or `menu`. Only requests mode sends `sourceQuantities`.
A null participant means the server-resolved current chef, never a supplied identity.
Catalogue keys are `dish:<id>`. Chef hand-written dishes have `dishId:null`, a unique
`customId`, and the key `custom:<customId>`; no fabricated catalogue IDs. Their source
names and metadata synchronize with the final menu. Public custom rows are read-only.

Menu proposals allow integers 0–9999, up to 200 rows, names up to 128 characters,
item notes up to 255 and meal notes up to 1000. Chef allocation supports up to 9999;
ordinary public sources support up to 99. The server checks source totals against
final targets and validates availability for every addition/increase.

All sources, source revisions, batch locking and the published snapshot live in one
private `MealTicketState`. Commands work on a detached copy and make one versioned
CAS write. Stale state/menu/source versions, failures and duplicate commits cannot
partially change sources, lock a batch or publish a menu.

Only nonempty confirmed meals appear on 当日菜单. Later pending choices never silently
change that published snapshot. The public page title is `当日菜单 - 柠檬私厨` or
`看看吃啥 - 柠檬私厨`; branding can configure the suffix.

## Catalogue and validation

A dish uses `tags:string[]`; meal tags and other tags are separate form/table groups.
There are no meal-period IDs. `isAvailable` and `soldOut` are separate boolean columns,
edited in the dish form. No old orders, backup, notification or random-menu endpoints
are registered.

Checks cover ownership, integer inputs, stock, shared batches, source ordering,
chef allocation, atomic failure/conflicts, zero submission, custom identity/rename,
source text preservation, read-only submitted public rows and menu publication.
