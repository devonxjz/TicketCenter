# Quy trình Git và bàn giao cho năm thành viên

Áp dụng cho [Khánh](khanh.md), [Đông](dong.md), [Liêm](liem.md), [Thái](thai.md), [Vương](vuong.md) và [TEAM-CONTRACT](TEAM-CONTRACT.md). Cập nhật 06/10/2026 từ tài liệu GitFlow đính kèm, điều chỉnh cho đồ án cuối kỳ. Xem [đánh giá hướng dẫn gốc](GITFLOW-GUIDE-REVIEW.md). Quy trình hiện hành chỉ dùng `main`, `develop` và `feature/<ten>/<task-id>-<chuc-nang>`. Tính năng mới và sửa lỗi dùng cùng loại nhánh task; không có nhánh release hoặc loại nhánh sửa khẩn cấp riêng. Release là tag phiên bản và hồ sơ bàn giao trên commit đã kiểm ở main.

Đây là tài liệu hướng dẫn triển khai, không xác nhận đã có nhánh develop, branch protection, CI, staging hay bản phát hành. Việc đọc/soạn tài liệu không tự cho phép push, merge, tạo tag remote, deploy hoặc đổi cấu hình GitHub. Workspace mới chưa khởi tạo Git; workspace cũ có lịch sử và thay đổi chưa commit, không reset hoặc stage tất cả để làm lại baseline.

## 1. Nhánh và luồng tích hợp

| Nhánh | Rẽ từ | Nhận thay đổi / đích PR | Vai trò |
|---|---|---|---|
| `main` | Baseline khởi tạo đã kiểm | PR `develop → main`; ngoại lệ PR feature sửa bản đã bàn giao theo §7 | Bản đã nghiệm thu theo mốc; tài liệu baseline ban đầu chưa phải phần mềm hoàn thành |
| `develop` | main sau baseline | PR feature; đồng bộ main sau phát hành hoặc sửa bản đã bàn giao | Ghép liên miền và kiểm thử M0–M4 |
| `feature/<ten>/<task-id>-<chuc-nang>` | Mặc định develop; ngoại lệ main theo §7 | Mặc định PR vào develop; ngoại lệ PR vào main rồi đồng bộ main → develop | Một task tính năng hoặc sửa lỗi, hay phần có thể review độc lập của task |

Luồng bình thường: `develop → feature → PR → develop → PR nghiệm thu → main → tag`. Sau phát hành, mở PR `main → develop` nếu có commit của main chưa được đồng bộ; làm trước đợt phát triển tiếp theo. Lỗi trong quá trình phát triển sửa trên feature từ develop, commit dùng `fix(scope): ...`. Nếu bản đã bàn giao có lỗi và develop chứa thay đổi chưa nghiệm thu, dùng cùng loại nhánh feature từ main: `main → feature sửa lỗi → PR → main → PR → develop`; điều kiện cụ thể ở §7.

Không code/commit trực tiếp trên main hoặc develop sau bootstrap; thay tài liệu cũng qua nhánh task và PR. Chỉ ngoại lệ baseline đầu tiên của repository mới do người được giao khởi tạo, trước khi áp dụng bảo vệ nhánh. Không dùng một nhánh cá nhân kéo dài cho cả dự án, không đưa feature chưa nghiệm thu trực tiếp vào main. Bảng 76 nhánh thành viên mặc định rẽ từ develop; ngoại lệ §7 chỉ dành cho sửa bản đã bàn giao, cần ghi rõ base/đích PR và lý do.

Tên ASCII chữ thường, dấu gạch nối; tên người là `khanh`, `dong`, `liem`, `thai`, `vuong`. Giữ task ID ổn định, dùng chữ thường trong tên nhánh. Không dùng số Issue giả, tên `final`, `test1` hoặc ngày tùy tiện.

| Thành viên | Phạm vi | Ví dụ đúng |
|---|---|---|
| Khánh | identity, auth, core, layout, mail | `feature/khanh/khanh-08-email-otp` |
| Đông | organization, membership, event, zone, seat, storage | `feature/dong/dong-09-publish-event` |
| Liêm | ticketing, order, coupon, payment, ticket, outbox | `feature/liem/liem-10-payment-result` |
| Thái | check-in, refund, compensation, cancellation | `feature/thai/thai-10-refund-result` |
| Vương | commission, settlement, reports, audit, db, ci, delivery | `feature/vuong/vuong-07-confirm-settlement` |

Bảng từng thành viên cung cấp đủ 76 tên nhánh theo nhiệm vụ. PR con thêm suffix rõ như `-part-1`; không thay task ID. Kiểm working tree trước khi chuyển nhánh. Thay đổi người khác chưa commit phải được bảo toàn; dùng checkout/worktree được giao, không reset/stash toàn bộ một cách mù quáng.

## 2. Khởi tạo baseline cho workspace mới

Vương điều phối tích hợp theo phân công; người khởi tạo và người có quyền quản trị GitHub do nhóm giao thực tế, không suy ra từ tên vai trò. Trước bootstrap, kiểm bộ tài liệu, đường dẫn, bí mật, file được ignore và danh tính Git của người commit. Không mang `.git` cũ, target, cấu hình IDE hoặc credentials vào repository mới; không xóa workspace cũ để khởi tạo.

Ví dụ sau chỉ chạy khi đang ở workspace mới chưa có `.git`, Git hỗ trợ `init -b` và nội dung baseline đã được review:

```powershell
git init -b main
git status --short
git add -- .gitignore README.md spec.md pom.xml docs
git add -- 'Báo cáo phân công - Nhóm 9.docx'
git diff --cached --check
git diff --cached
git commit -m 'chore(repo): establish the reviewed project baseline'
git switch -c develop
```

`docs` trong lệnh chỉ được stage sau khi đã kiểm toàn bộ nội dung; file Word phân công phải đi cùng baseline nếu các README vẫn tham chiếu nó. Ví dụ minh họa một commit baseline; có thể chia thành các commit logic, nhưng nguồn và mọi tài liệu tham chiếu phải đủ khi bàn giao. Không tạo lịch sử đóng góp, ngày hoặc co-author giả. Baseline tài liệu không được gắn nhãn nghiệm thu ứng dụng hoặc tag v1.0.0.

Remote là repository mới có URL đã được xác nhận, không tự dùng origin của workspace cũ. Khi được giao quyền, cấu hình remote, push main/develop và bật quy tắc PR/checks. Nếu remote có commit khởi tạo sẵn, kiểm và tích hợp lịch sử đó trước; không force-push đè. Không chạy push từ ví dụ nếu người triển khai chưa được phép.

## 3. Bắt đầu task và cập nhật develop

Luồng task mặc định dưới đây chỉ khi checkout sạch, develop/origin thật tồn tại và được quyền fetch/pull. Ngoại lệ sửa bản đã bàn giao không chạy nguyên mẫu này mà xác định base theo §7:

```powershell
git status --short
git fetch origin
git switch develop
git pull --ff-only origin develop
git switch -c feature/thai/thai-10-refund-result
```

Nếu chưa có baseline/develop thì ghi blocker bootstrap, không tự rẽ từ main để vượt hợp đồng. Phần công việc độc lập có thể chuẩn bị theo artifact được giao nhưng không báo đã ghép.

Khi develop thay đổi, cập nhật trên nhánh feature có base develop. Nhánh sửa bản đã bàn giao có base main chỉ cập nhật từ main; không merge develop chưa nghiệm thu vào nhánh đó. Mặc định merge develop vào feature để giữ lịch sử nhánh đã chia sẻ:

```powershell
git fetch origin
git merge origin/develop
```

Rebase chỉ áp dụng cho nhánh cá nhân chưa push, chưa có người khác dựa vào, với working tree sạch. Nhánh đã chia sẻ dùng merge; ngoại lệ rewrite cần chủ nhánh/người phụ thuộc phối hợp và được giao quyền riêng. Không coi `--force-with-lease` là bảo đảm tuyệt đối: nếu ngoại lệ được phép thì kiểm SHA remote kỳ vọng cụ thể, không dùng lease chung trong môi trường tự fetch. Tuyệt đối không rewrite/force-push main hoặc develop.

Conflict: đọc cả hai phía, phối hợp chủ file và caller, giữ contracts/migration; không chọn ours/theirs toàn file. Trong merge, stage file đã giải quyết rồi hoàn tất merge; trong rebase dùng `git rebase --continue`, không dùng lệnh commit thay thế chung. Sau xử lý, chạy lại kiểm chứng liên quan trên bản đã cập nhật.

## 4. Commit chuẩn và thời điểm commit

Dùng [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/): `type(scope): description`. Subject và body commit dùng tiếng Anh; subject động từ hiện tại rõ hành vi, khuyến nghị tối đa 72 ký tự. Type gồm feat/fix/docs/test/refactor/perf/build/ci/chore/style. Scope theo miền: identity/core/organization/event/ticketing/order/coupon/payment/fulfillment/settlement/report/audit/jobs/db/build/ci/docs; style chỉ format, refactor không đổi hành vi.

Giữ scope miền để đọc lịch sử theo nghiệp vụ; ticket/task đặt ở footer, thay cho quy ước `type(#ticket)` của hướng dẫn gốc. `Task-Id` là mã trong file thành viên, không phải số Issue GitHub. Nếu Issue thật tồn tại thì thêm `Refs: #...`; chỉ dùng `Closes #...` khi PR đóng trọn phạm vi Issue thật. Không bịa ticket hoặc đưa placeholder vào commit thực.

```text
feat(fulfillment): apply verified refund results atomically

Keep the attempt log, ticket state and inventory return in one transaction.
Replayed provider results do not credit the customer or inventory twice.

Task-Id: THAI-10
Validation: ThaiRefundResultIT; database/tests/thai/THAI-10.sql
Evidence: docs/evidence/thai/THAI-10.md
```

- Hoàn thành một đơn vị hành vi có kiểm chứng thì commit code + test + migration/DTO/tài liệu liên quan; không chờ cuối tuần hoặc toàn module.
- Task lớn có nhiều commit/PR con: nền contract/schema → hành vi/test → UI/handoff. Commit nền không chứng minh toàn task hoàn thành; không bắt buộc một file/checkbox là một commit.
- Checkpoint chưa đủ dependency dùng subject đúng type như `chore(core): save an incomplete transaction scaffold`, Task-Id và body nêu blocker; không dùng `WIP: ...` ngoài convention, không merge checkpoint chưa đạt vào develop/main.
- Fix sau review dùng `fix(scope): ...`; thay đổi phá hợp đồng dùng `!`/`BREAKING CHANGE:`, cập nhật caller/test/migration và phối hợp owner trước ghép.
- Dùng danh tính thật của người commit, không đoán GitHub handle hoặc thêm co-author giả.

Trước commit: kiểm diff/ownership, chạy checks đúng task, `git diff --check`, stage đường dẫn cụ thể, xem `git diff --cached` và `git status --short`. Không `git add .` ở workspace có thay đổi người khác. Body nhiều dòng dùng file UTF-8 không BOM và `git commit -F <file-message>`; file tạm thuộc người tạo được dọn sau dùng.

Không commit credentials/.env, cookie/OTP/raw QR, dữ liệu cá nhân, simulator state, target/WAR sinh, log chưa lọc hoặc runtime download. Source/WAR/database backup dùng gói bàn giao đúng chính sách; evidence đã lọc và SQL plan thuộc task có thể lưu trong source theo quy định.

## 5. PR và review trên hai nhánh chung

Mặc định một task → một Issue thật khi có → một nhánh feature → một PR vào develop. Ngoại lệ sửa bản đã bàn giao có PR feature → main và PR đồng bộ main → develop theo §7; không bỏ bước đồng bộ. Task lớn có PR con ghi `THAI-10 / part 1` và tiêu chí riêng. Title PR theo Conventional Commits, Task-Id đặt trong body. Không bịa số Issue hoặc đóng Issue bằng PR chỉ làm một phần.

```markdown
Task: THAI-10
Base: develop
Problem/result: Kết quả hoàn lặp có thể trả kho hai lần; SP11 cập nhật
nghĩa vụ, attempt và kho nguyên tử, replay giữ kết quả cũ.
Scope: SP11/TX11, RefundTransferRepository, IX08 và ca nghiệm thu.
Dependencies: Liêm về Ticket; Đông về inventory; Vương về lock contract.
Contracts/migrations: File SQL mới, DTO/API và mọi caller bị ảnh hưởng.
Validation: Lệnh, môi trường, expected/actual, exit code; link evidence đã lọc.
Limitations: Điều kiện chưa kiểm, lý do, người cung cấp.
Reviewers: Chủ miền/người nhận; thêm reviewer tiền/quyền khi liên quan.
```

| Đích PR | Review tối thiểu | Kiểm chứng |
|---|---|---|
| develop | 1 người không phải tác giả; tiền/quyền/transaction/hợp đồng liên miền cần 2 người chuyên môn | Checks đúng task; dependency được ghép và bản tích hợp đạt |
| main | 2 người không phải tác giả của phần cần review | Evidence bản nghiệm thu, build/unit/SQL/browser liên quan, fresh-install/runbook/hồ sơ theo mốc |

Vương điều phối tích hợp, người có quyền merge do nhóm giao; không đồng nghĩa Vương là reviewer duy nhất. Tác giả không tự approve. Dismiss review cũ hoặc yêu cầu review lại khi thay đổi ảnh hưởng kết luận. Shared files pom/web.xml/DTO/locking/manifest phải phối hợp owner; không tự ghi đè để qua checks.

Main và develop yêu cầu PR, checks phù hợp, chặn force-push/xóa nhánh chung và direct-push sau bootstrap. Quy tắc chỉ được coi đã cưỡng chế khi có cấu hình GitHub thật và quyền/gói tài khoản hỗ trợ; nếu thiếu thì ghi quy trình review/merge thủ công và giới hạn thực. Hai reviewer theo miền là policy nhóm, không giả đã cấu hình theo từng đường dẫn.

Mặc định dùng merge commit để giữ commit task và các lần tích hợp; không bật required linear history cùng lựa chọn này. Squash chỉ khi nhóm thống nhất cho PR task có checkpoint; message cuối vẫn đủ Task-Id/evidence và tác giả thật. PR develop → main và PR đồng bộ main → develop dùng merge commit để giữ quan hệ tổ tiên. Không chọn merge method tùy tiện cho từng người.

Ghép M0 theo artifact → M1 → M2 → M3 → M4 trên develop. Không đợi toàn bộ file của một người xong; dependency thật phải đi trước caller, Draft PR không là dependency đã đạt. Sau merge có smoke bản ghép và evidence, cập nhật task/Issue/contributions. Xóa nhánh feature chỉ sau merge/review, không còn dependency và có quyền của chủ nhánh; main/develop luôn giữ. Ưu tiên dọn nhánh task thủ công; chỉ bật auto-delete head branch khi main/develop đã được bảo vệ khỏi xóa và hành vi này đã được kiểm, tránh xóa develop sau PR vào main.

## 6. Chốt phiên bản cuối kỳ không có nhánh release

Main là nơi giữ bản đã đạt theo mốc; không cần nhánh release vì nhóm không duy trì nhiều dòng phiên bản. Khi chuẩn bị nộp/demo:

1. Vương phối hợp nhóm chốt task/phạm vi và tạm dừng nhận tính năng mới vào develop; feature chưa đạt không được ghép để vừa hạn.
2. Ghi SHA ứng viên của develop. Chạy checks đúng mốc và môi trường thật; thiếu DB/provider/browser ghi BLOCKED đúng phần. M4 cần fresh-clone/install, migration/seed/quyền, WAR/luồng UI, restore, evidence và hồ sơ theo spec.
3. Lỗi ứng viên sửa bằng nhánh feature theo task từ develop, commit fix, qua PR; cập nhật SHA, chạy lại checks bị ảnh hưởng và smoke bản ghép. Không sửa trực tiếp nhánh chung hoặc migration đã áp dụng.
4. Tạo PR develop → main với task/mốc, SHA ứng viên, evidence, thay đổi schema/config, giới hạn và gói bàn giao. Hai reviewer chấp nhận đúng revision; main/develop thay đổi thì đánh giá lại bản ghép, không dùng kết quả cũ.
5. Merge bằng merge commit, kiểm đúng commit main sau merge. Chỉ tạo tag trên SHA main đã kiểm khi mốc thực đạt; không tag ứng viên trên feature/develop trước merge, không tag tự động mỗi PR tài liệu.
6. Khi được phép tạo bản phát hành, dùng annotated tag chưa tồn tại, ví dụ `v0.1.0-m1`, `v0.2.0-m2`, `v0.3.0-m3`, `v1.0.0` cho nghiệm thu M4. Ghi SHA cụ thể, người chốt, evidence và checksum gói bàn giao. Tag đã công bố không di chuyển/xóa để che lỗi; bản sửa có phiên bản mới như v1.0.1.
7. Có thể tạo GitHub Release từ tag với release notes và source/WAR/SQL hoặc backup đã lọc theo yêu cầu môn học; không đóng gói credentials/dữ liệu thật. Đây là thao tác xuất bản riêng, cần quyền người giao việc.
8. Đồng bộ main về develop qua PR khi cần, chạy smoke, rồi mở lại đợt phát triển tiếp theo. Feature đang làm cập nhật develop trên nhánh của mình.

Ví dụ tag sau kiểm chứng, không tự thực thi; thay giá trị SHA bằng commit main thật đã kiểm và kiểm tag chưa tồn tại:

```powershell
$taskVerifiedMainSha = '<SHA main đã kiểm>'
git tag -a v1.0.0 $taskVerifiedMainSha -m 'TicketsCenter final project release'
```

Push tag/Release/deploy/nộp hồ sơ chỉ theo quyền được giao; không suy ra từ việc tạo tag local. GitHub Release dựa trên tag, không đòi hỏi nhánh release riêng.

## 7. Sửa lỗi bằng nhánh task chung

Trước khi bàn giao, mọi lỗi dùng `feature/<ten>/<task-id>-<chuc-nang>` rẽ từ develop, commit `fix(scope): ...`, PR vào develop; bản đã đạt đi qua PR develop → main như bình thường. Không tạo thêm loại nhánh hoặc quy trình khẩn cấp riêng.

Sau khi bàn giao, nếu develop vẫn là ứng viên có thể nghiệm thu thì dùng luồng mặc định. Chỉ khi bản trên main đã được nghiệm thu/bàn giao có lỗi và develop chứa công việc chưa nghiệm thu mới dùng ngoại lệ sau:

1. Ghi task ID thật, owner, phiên bản/tag bị lỗi, cách tái hiện và lý do chưa thể phát hành develop. Giữ phạm vi sửa nhỏ; không thêm tính năng hoặc kéo toàn bộ develop vào bản đã nộp.
2. Từ checkout sạch, tạo nhánh `feature/<ten>/<task-id>-<mo-ta-sua-loi>` trên main mới nhất đã xác nhận. Đây vẫn là nhánh task chung, không dùng prefix khác. Nếu cần cập nhật base thì merge main, không merge develop chưa đạt.
3. Commit fix theo convention, giữ Task-Id/Validation/Evidence, chạy regression và checks liên quan bằng đúng môi trường. PR ghi `Base: main`, phiên bản bị ảnh hưởng và lý do ngoại lệ; cần hai reviewer như mọi PR vào main, không bỏ kiểm tiền/quyền vì hạn nộp.
4. Sau merge, kiểm đúng SHA main. Nếu được phép phát hành bản sửa, tạo tag patch mới như v1.0.1 và gói bàn giao theo §6; giữ nguyên tag v1.0.0 đã công bố.
5. Mở PR `main → develop`, xử lý conflict cùng chủ file, kiểm lại phần liên quan để bản phát triển tiếp theo có cùng bản sửa. Chỉ đóng task sửa khi cả bản main và bước đồng bộ develop đã có bằng chứng, hoặc ghi rõ BLOCKED cho phần đồng bộ còn thiếu.
6. Nếu develop đang chốt ứng viên cuối kỳ, cập nhật SHA ứng viên và chạy lại checks liên quan. Dọn nhánh feature khi đã ghép, không còn dependency và có quyền của chủ nhánh. Ghi nguyên nhân và cách phòng lỗi trong evidence/runbook.

Ngoại lệ chỉ thay base/đích PR của nhánh task; main/develop vẫn không nhận commit trực tiếp và không cho phép reset/force-push nhánh chung. Khởi tạo baseline tài liệu chưa phải bản đã bàn giao nên không được dùng ngoại lệ này để vượt bootstrap/dependency.

## 8. Xử lý nhầm nhánh và lịch sử chung

- Local thay đổi chưa commit: xem diff/ownership, checkpoint đúng convention hoặc stash đúng đường dẫn của mình; stash mặc định không giữ untracked nên kiểm rõ danh sách. Không stash/reset thay đổi của cả nhóm để chuyển nhánh.
- Commit nhầm chưa push: bảo toàn bằng nhánh cứu hộ/patch trước, chuyển phần đúng sang feature bằng cherry-pick sau kiểm tra. Chỉ sửa ref local khi đã xác minh commit chưa chia sẻ, vị trí commit và working tree; không dùng mẫu reset HEAD~1 như giải pháp chung.
- Commit nhầm đã push lên nhánh chung: không reset/force-push bỏ lịch sử; mở PR sửa/revert có kiểm phụ thuộc. Không cherry-pick lại commit đã nằm trong ancestry mà không kiểm để tránh nhân thay đổi.
- Push non-fast-forward: fetch, xem graph và xác định commit người khác; merge cập nhật hoặc phối hợp owner. Không tự rebase/force để làm push thành công.
- Revert source không tự hoàn tác dữ liệu database. Migration đã áp dụng dùng migration sửa tiến hoặc runbook đã kiểm, không sửa file cũ và tuyên bố rollback hoàn tất.

## 9. CI, môi trường và quyền thực hiện

Khánh chuẩn bị build/test profiles; Vương điều phối CI/checks/đóng gói theo ownership. CI dự kiến chạy trên PR vào develop/main và trên bản ghép: build/unit, SQL Server IT/quyền/cạnh tranh cho phạm vi SQL, browser khi liên quan UI. Maven là công cụ dự án; không dùng npm test làm bằng chứng backend Java.

Nhánh không tự tạo staging/production hoặc pipeline deploy. Với đồ án, ưu tiên CI kiểm chứng và deploy demo có bước cho phép riêng. Nếu sau này tự động deploy, chốt một trigger (tag phát hành đã duyệt hoặc bước thủ công) để tránh deploy hai lần từ cả merge main và tag. Credentials lấy từ môi trường/secrets store; không lưu trong source hoặc log.

Môi trường thiếu ghi BLOCKED, không skip test bắt buộc rồi báo PASS. Required check name phải là check thật đã chạy. CODEOWNERS chỉ thêm khi có GitHub handle thật; auto-delete nhánh là cấu hình repository có thể ảnh hưởng develop, không phải mục bảo vệ từng nhánh độc lập.

## 10. Bằng chứng và tiêu chí đóng task

Task chỉ hoàn thành khi behavior/SQL/UI đạt, dependency thật đã ghép, contracts/caller/docs cập nhật, reviewer chấp nhận và evidence đủ. Merge vào develop chưa phải bản nộp; main/tag cũng không thay bằng chứng nghiệm thu. Checklist phản ánh kết quả thật.

Evidence ghi `testedRevision` (SHA đã kiểm, hoặc SHA nền + diff working tree trước commit), fixture/môi trường/lệnh/expected/actual/exit code. PR/CI ghi SHA thật; bản phát hành ghi thêm main SHA/tag/checksum. Không rewrite commit liên tục để nhét SHA của chính commit chứa evidence vào nó.

Giữ Issue labels member/domain/phase:M0..M4/blocked/needs-contract/needs-sql-evidence; trạng thái Todo/In progress/Review/Done. Thay spec/diagram/contracts qua quyết định có owner, ảnh hưởng/caller/tests; nguồn chuẩn đi trước triển khai. PR tiền/quyền kiểm một accepted capture, paidAmount totals, một nghĩa vụ/lần xử lý hiện hành, paid+pending<=net, GET/Return không ghi tiền, quyền hiện tại và zero/UNKNOWN/replay/recovery.

Runbook xử lý pending/UNKNOWN/poison job/USED exception qua đường được phép; không sửa trực tiếp tiền/kho/status DB để làm demo đẹp. Fresh-install/smoke sau từng mốc trên bản ghép; mock và môi trường thật ghi nhãn riêng. Quy trình Git không thêm nghiệp vụ ngoài spec.

Nguồn đối chiếu: [GitFlow gốc](../references/GitFlow_Workflow_Guide.docx), [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/), [Git push và force-with-lease](https://git-scm.com/docs/git-push), [GitHub protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches), [GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases). Quy tắc dự án trong file này thay các ví dụ không phù hợp của hướng dẫn gốc.
