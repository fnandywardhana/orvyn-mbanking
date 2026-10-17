# Rencana uji: hotfix 0.9.1

Penulis: QA, 17 Okt 2026. Sumber: `requirement.md`, `prd.md` (H-1..H-11), `stories.md` (H91-1..H91-5), `design.md`, `it-report.md` (commit `1c1394d..e523ae4`), dan temuan Q-02-F1..F5 di `docs/features/2026-10-16-quality-pass/qa-report.md` (Putaran 2, R2.5). Skenario disusun dengan skill `test-scenarios`: tujuan, kondisi awal, peran, langkah, dan hasil yang diharapkan per kriteria penerimaan.

## 0. Lingkungan, data, dan cara verifikasi

| Bagian | Nilai |
|---|---|
| Build | `main` e523ae4, debug **0.9.1 versionCode 13**. Untuk kompatibilitas: debug **0.9.0** dari commit `3534aeb` lewat `git worktree` di scratchpad |
| Server | Dev server lokal port 8790 (perintah di memori proyek, rahasia dari `.env` tanpa ditampilkan), PID disimpan dari `$!` dan dimatikan hanya dengan `kill <PID>`. **Tidak pernah `pkill -f`** |
| DB | `orvyn-dev-db-1` (127.0.0.1:55432), test server memakai `orvyn_test` |
| Admin | Admin dev `http://127.0.0.1:8790/admin`, petugas dev (password di memori proyek) |
| HP | Galaxy Note10+ pemilik, Android 12, `adb reverse tcp:8790 tcp:8790`. Font setelan pemilik, tidak diubah |
| Produksi | `orvyn-server-1`, `orvyn-db-1` tidak disentuh. Waktu mulai container dicatat di awal dan akhir sebagai bukti |

**Data uji:** Raka (Everyday, punya kartu kredit), Sinta (lawan transaksi, sebagian lewat API), Hendra (Prestige, deposito). Nomor, password, dan PIN di memori proyek, tidak ditulis di sini. Nasabah tanpa kartu kredit: nasabah daftar baru dari putaran sebelumnya (`QA Rondedua`, saldo Rp0) atau nasabah yang dibuat petugas di admin dev.

**Kode cara verifikasi:** sama dengan `docs/features/2026-10-16-quality-pass/test-plan.md` bagian 0.3: **HP**, **UIA** (`uiautomator dump`, pengganti TalkBack), **R200** (render Robolectric font 2,0, 360dp dan 411dp, terang dan gelap), **R** (test Compose lain), **API** (curl ke dev server), **SRV** (`npm test`), **ADM** (admin dev lewat curl/browser QA), **DB** (query baca), **KODE**.

**Font 200% dan TalkBack asli tidak diuji di HP** (setelan HP dilarang). Penggantinya R200 dan UIA.

**Buku besar (dilakukan di awal, setelah setiap aksi yang memindahkan uang, dan di akhir):** `checkLedger` (skrip kecil yang memanggil `server/src/ledger.ts` ke DB dev) harus OK, jumlah `journal_entries` naik tepat satu per aksi dengan `kind` yang diharapkan, tidak ada `kind` baru, dan tidak ada Posting bernilai 0 (`select count(*) from postings where amount = 0` = 0).

## 1. Aturan HP (wajib)

Sama persis dengan `docs/features/2026-10-16-quality-pass/test-plan.md` bagian 1. Ringkasnya:

1. Penjaga `mCurrentFocus` = `id.orvyn.app/id.orvyn.app.MainActivity` sebelum setiap tap, teks, swipe, dan keyevent, dalam satu skrip yang berhenti bila gagal. Di awal sesi ORVYN release 0.9.0 mungkin sedang dipakai pemilik: bila bukan ORVYN di depan, tidak ada input sama sekali.
2. Tidak pernah Back di beranda ORVYN. Sebelum Back selalu dump dan pastikan bukan beranda; lebih baik panah `Back` di layar. Setelah layar sukses dianggap di beranda.
3. Bila ORVYN tertutup: berhenti, buka lagi hanya dengan `am start`.
4. Ketuk hanya koordinat dari dump terbaru, di dalam jendela ORVYN.
5. Sheet sistem hanya bila dibuka ORVYN, dan hanya ditutup (menu bagikan dengan satu Back, izin kamera ditolak).
6. Kamera tidak pernah memotret.
7. Setelan HP tidak diubah. Offline hanya lewat `adb reverse --remove tcp:8790`. Terang/gelap lewat setelan Theme di ORVYN.
8. Tidak menyentuh aplikasi lain, panel notifikasi, atau Recents.
9. Screenshot hanya saat ORVYN di depan, status bar dipotong, disimpan ke `qa-shots/`.
10. Memasang debug berarti uninstall release, sesi ORVYN pemilik ikut keluar (praktik yang sudah diterima).
11. Akhir sesi: pasang lagi `releases/orvyn.apk` (0.9.0), `adb reverse --remove-all`, matikan dev server dan proxy dengan PID, hapus worktree, semua fitur dev menyala, maintenance off, tidak ada commit.

## 2. Otomatis dan build

| ID | Skenario | Diharapkan | Cara |
|---|---|---|---|
| A1 | `cd server && npm test` | Semua lolos (IT: 118), termasuk test baru H91-1 (`requests.test.ts`), H91-2 (`insights.test.ts`), H91-3 (`deposits.test.ts`) | SRV |
| A2 | `cd android && ./gradlew testDebugUnitTest --rerun-tasks`, **dua kali** berturut-turut | Kedua run lolos semua (IT: 106), termasuk `Hotfix091Test` | R |
| A3 | `./gradlew assembleDebug`, `aapt2 dump badging` | BUILD SUCCESSFUL, `versionName='0.9.1'`, `versionCode='13'` | KODE |
| A4 | `aapt2 dump permissions` debug 0.9.1 vs `releases/orvyn-0.9.0.apk` | Daftar `uses-permission` identik | KODE |
| A5 | Copy baru `design.md` 2 dan 3 dicari di `android/app/src/main` dan `server/src` | Ditemukan persis (template `{minimum}` boleh berupa interpolasi) | KODE |
| A6 | Rahasia di commit `1c1394d^..e523ae4` | Tidak ada `.env`, `secrets/`, `*.jks`, `signing.properties`, password | KODE |
| A7 | Buku besar awal | OK, jumlah Journal Entry dan Posting 0 dicatat | DB |

## 3. Skenario per story

### H91-1 Setiap permintaan uang bisa dibayar (Q-02-F2)

**Tujuan:** permintaan uang dan bagian split hanya bisa dibuat bila bisa dibayar (minimal `{minimum}` = Rp10,000), dan aplikasi menjelaskan minimumnya sebelum nasabah mencoba.
**Kondisi awal:** debug 0.9.1, Raka masuk, fitur `requests` menyala, `/config.minTransfer` = 10000. **Peran:** nasabah (Raka), pembayar (Sinta).

| ID | AC | Langkah | Diharapkan | Cara |
|---|---|---|---|---|
| H1-1 | 1 | Requests → `Ask for money` → Sinta → layar nominal: kosong, ketik 5, 9999, 10000 | Rp0 `Enter an amount` nonaktif; Rp5 dan Rp9,999 `Minimum Rp10,000` nonaktif, tanpa error inline; Rp10,000 `Continue` aktif. Chip 50k/100k/250k/500k tampil | HP, UIA |
| H1-2 | 1 | Requests → `Split a bill` → total: kosong, 9999, 10000 | `Enter an amount` / `Minimum Rp10,000` / `Next` aktif | HP, UIA |
| H1-3 | 2 | Split Rp15,000 dengan Sinta → `Check the shares` → ketuk baris Sinta → sheet | Kalimat `The smallest share is Rp10,000.` di bawah judul `Sinta's share`. Rp5,000 → `Minimum Rp10,000` nonaktif; Rp10,000 → `Save` aktif | HP, UIA |
| H1-4 | 3 | Split Rp15,000 dengan satu teman (rata Rp7,500) | Baris teman: ikon + `Needs at least Rp10,000`; tombol `Minimum Rp10,000 a share` nonaktif. Ubah ke Rp10,000 → tanda hilang, `Send 1 request` aktif, `Your share` Rp5,000. Label node baris: `…, Needs at least Rp10,000` | HP, UIA, screenshot |
| H1-5 | 4 | Split Rp15,000 dengan dua teman | `This bill is too small for 2 friends at Rp10,000 each. Go back and pick fewer friends.`, tombol kirim nonaktif. Panah kembali → `Who's in?` dengan kedua teman tetap tercentang (`checked=true`) | HP, UIA |
| H1-6 | 5 | Server: buat permintaan Rp9,999, split total Rp9,999, split bagian Rp9,999, lalu Rp10,000 | `BAD_AMOUNT` dengan tiga pesan `design.md` 2.5, jumlah `money_requests` dan inbox Sinta tidak bertambah; Rp10,000 diterima | SRV, API, DB |
| H1-7 | 5 | Debug 0.9.0: minta uang Rp5,000 → `Check the request` → kirim | Kotak error `Ask for at least Rp10,000.`, tidak ada permintaan baru | HP (0.9.0) |
| H1-8 | 5 | Detail transaksi Raka: QRIS/tagihan < Rp10,000 dan ≥ Rp10,000 | `Split this bill` tidak tampil untuk yang < Rp10,000, tampil untuk ≥ Rp10,000 | HP, UIA |
| H1-9 | 6 | Raka minta Rp10,000 dari Sinta (HP) → Sinta bayar (HP sebagai Sinta, layar periksa + PIN) | Tepat satu `internal_transfer` Rp10,000, status permintaan `paid`, buku besar OK | HP, DB |
| H1-10 | 6 | Minimum dinaikkan ke Rp15,000 setelah permintaan Rp10,000 dibuat → bayar | Pembayaran berhasil Rp10,000 (H-9) | SRV |

**Kasus tepi H91-1:**

| ID | Skenario | Diharapkan | Cara |
|---|---|---|---|
| H1-E1 | Ketuk ganda `Send 1 request` / `Ask for Rp10,000` | Tepat satu permintaan | HP, DB |
| H1-E2 | Offline di `Check the request` → kirim | Kotak `You're offline. Check your connection and try again.`, tidak ada permintaan | HP |
| H1-E3 | PIN salah saat Sinta membayar | `Wrong PIN. 4 tries left.`, tidak ada Journal Entry | HP atau API |
| H1-E4 | Saldo kurang saat membayar (nasabah Rp0) | Tombol nonaktif / `You're Rp… short.`, tidak ada Journal Entry | HP atau API |
| H1-E5 | Fitur `requests` dimatikan di admin | Requests tidak tersedia, `Split this bill` tidak tampil; dinyalakan lagi | ADM, HP |
| H1-E6 | Permintaan dev lama Rp5,000 (dari QA putaran 2) dibayar | Bisa dibayar (PRD 5, H-9), satu `internal_transfer` Rp5,000 | API, DB |
| H1-E7 | Bagian teman di atas total (0.9.0, c225570) | Kalimat `Your friends' shares add up to more than the bill.` dengan ikon, tombol nonaktif | HP |
| H1-E8 | Terang dan gelap, font 200% | Label tombol dan baris tanda terbaca utuh | HP (tema ORVYN), R200 |

### H91-2 Detail kategori Insights cocok dengan totalnya (Q-02-F3)

**Tujuan:** detail kategori memuat tabungan dan kartu kredit, jumlahnya sama dengan nominal kategori.
**Kondisi awal:** Raka punya belanja kartu kredit bulan ini. **Peran:** nasabah.

| ID | AC | Langkah | Diharapkan | Cara |
|---|---|---|---|---|
| H2-1 | 1 | History → Insights → `Shopping` | Baris dari tabungan dan kartu; jumlah semua baris = nominal `Shopping` di Insights | HP, UIA, API |
| H2-2 | 2 | Lihat subjudul dan baris kartu, terang dan gelap | `This month, from ORVYN Everyday and your cards.`; baris kartu ikon kartu, subjudul `{kartu} ·••• {4} · {hari}, {jam}`; label node `…, {kartu} card ending {4}, …` tanpa titik | HP, UIA, screenshot |
| H2-3 | 3 | Urutan; ketuk baris tabungan; ketuk baris kartu | Terbaru di atas campur sumber; tabungan → detail transaksi; kartu → detail sisi kartu (sama dengan dari Cards) | HP |
| H2-4 | 4 | Kategori yang bulan ini hanya punya belanja kartu (dibuat lewat simulasi transaksi kartu di admin, atau test) | Baris kartu tampil, bukan keadaan kosong | ADM+HP atau SRV |
| H2-5 | 5 | Offline → buka detail kategori → online → `Try again` | Error state `You're offline. Check your connection and try again.` + `Try again`, tanpa daftar cache; lalu daftar lengkap | HP |
| H2-6 | 5 | Nasabah tanpa kartu kredit | Hanya tabungan, jumlah = nominal kategori | API atau HP |
| H2-7 | 6 | Font 200%, 360dp dan 411dp, terang dan gelap | Nominal, nama, nama kartu terbaca utuh | R200 |
| H2-E1 | | APK 0.9.0 terhadap server 0.9.1: Insights → detail kategori | Tidak crash, daftar lama (tabungan) tetap tampil | HP (0.9.0) |
| H2-E2 | | Fitur `insights` dimatikan | Insights tidak tersedia; dinyalakan lagi | ADM, HP |

### H91-3 Deposito dengan bunga Rp0 (Q-02-F4)

**Tujuan:** `Mature now` dan job jatuh tempo berhasil dengan bunga Rp0, tidak ada Posting 0, satu deposito gagal tidak menahan yang lain.
**Kondisi awal:** Hendra masuk, saldo cukup. **Peran:** nasabah (Hendra), petugas (admin dev).

| ID | AC | Langkah | Diharapkan | Cara |
|---|---|---|---|---|
| H3-1 | 1 | Hendra buka deposito Rp1,000,000 hari ini (HP, PIN) → `At maturity` → `Pay out` → admin `Mature now` | Admin `Matured. Interest after tax: Rp0.`, tidak HTTP 500; Everyday +Rp1,000,000; deposito hilang dari Wealth; inbox `Deposit paid out` | HP, ADM |
| H3-2 | 2 | DB setelah H3-1 | Satu `deposit_close` dengan dua Posting (TD −1,000,000, Savings +1,000,000); tidak ada Posting 0; buku besar OK | DB |
| H3-3 | 3 | Deposito bunga > 0 (empat Posting); pajak dibulatkan ke 0 | Empat Posting; Posting pajak 0 tidak ditulis | SRV |
| H3-4 | 4 | Deposito `Renew` bunga Rp0 → `Mature now` | Diperpanjang, tanpa Journal Entry, inbox `Deposit renewed` | ADM, DB, SRV |
| H3-5 | 5 | Job: dua deposito jatuh tempo menit yang sama, yang pertama gagal | Yang kedua cair; yang pertama tetap terbuka, log `failed to mature`, dicoba lagi | SRV |
| H3-6 | 6 | Suku bunga 0% → job → `Pay out` | Satu `deposit_close` dua Posting | SRV |
| H3-E1 | | Buka deposito dengan PIN salah | `Wrong PIN. 4 tries left.`, tanpa Journal Entry | HP |

### H91-4 Done di keyboard (Q-02-F1)

**Tujuan:** Done/Enter menjalankan tombol utama bila aktif, hanya menutup keyboard bila nonaktif, Search hanya menutup keyboard. Enter fisik = `keyevent 66`.
**Kondisi awal:** Raka masuk, keyboard Samsung pemilik. **Peran:** nasabah.

| ID | AC | Langkah | Diharapkan | Cara |
|---|---|---|---|---|
| H4-1 | 1 | Transfer → `New recipient` → bank → nomor 5 digit → Enter; lalu 10 digit → Enter | Pendek: keyboard tertutup, layar tetap; valid: `Check account` berjalan (layar periksa nama) | HP, `dumpsys input_method` |
| H4-2 | 2 | Pay → `PLN token` → meter 6 digit → Enter; 12 digit → Enter. Sampel BPJS atau e-wallet | Belum lengkap: keyboard tertutup; lengkap: `Continue` | HP |
| H4-3 | 3 | Ask for money → `ORVYN account number` 10 digit → Enter (dan 5 digit); sheet simpan penerima `Nickname` → Enter; Change password: Next di `Current password`, Enter di `New password` | `Find` berjalan; `Save`; fokus pindah ke `New password`, lalu `Save password` bila aktif (diuji dengan password salah supaya tidak mengganti password, atau password yang sama) | HP |
| H4-4 | 4 | Note review Transfer → Enter; Note `Check the request` → Enter; `What for` `Check the shares` → Enter (aktif dan nonaktif) | PIN pad terbuka tanpa uang bergerak; permintaan terkirim; split terkirim; nonaktif hanya menutup keyboard | HP, DB |
| H4-5 | 5 | Search: Find a bank, cari penerima, cari History, cari Help → Enter | Keyboard tertutup, hasil tetap, layar tidak berganti | HP |
| H4-6 | 6 | Sign in Next/Done, kantong, Vyna Enter | Seperti 0.9.0; Enter di Vyna membuat baris baru, tidak mengirim | HP, KODE |

### H91-5 Snackbar beranda tidak menutupi QRIS (Q-02-F5)

| ID | AC | Langkah | Diharapkan | Cara |
|---|---|---|---|---|
| H5-1 | 1 | Beranda offline → ketuk `Transfer`, terang dan gelap | Tepi bawah snackbar ±12dp (≈32px di 420dpi) di atas tepi atas tombol QRIS; QRIS, bayangan, label tidak tertutup | HP, screenshot, UIA bounds |
| H5-2 | 2 | Saat snackbar tampil, ketuk tab History lalu kembali ke Home lewat tab | Tab bereaksi | HP |
| H5-3 | 3 | Font 200%, 360dp dan 411dp | Snackbar tidak menutupi QRIS | R (`Hotfix091Test`) |
| H5-4 | 4 | Tab lain dengan bottom nav | Anchor sama (satu `BottomBar`) | KODE |
| H5-5 | 5 | Sukses Transfer offline → `Save as a recipient` | Snackbar di atas tombol itu, tidak menutupi `Share receipt`/`Done` | HP |

## 4. Uji ulang Q-02-F1..F5

| Temuan | Uji ulang dengan langkah asli | Skenario |
|---|---|---|
| Q-02-F1 | New recipient BCA 10 digit → Enter; PLN meter 12 digit → Enter | H4-1, H4-2 |
| Q-02-F2 | Split Sinta Rp5,000 tidak bisa dibuat (aplikasi dan server) | H1-4, H1-6 |
| Q-02-F3 | Insights `Shopping` total = isi detail | H2-1 |
| Q-02-F4 | Hendra deposito hari ini `Pay out` → `Mature now` | H3-1, H3-2 |
| Q-02-F5 | Beranda offline → `Transfer` → snackbar | H5-1 |

## 5. Regresi cepat alur uang di HP (0.9.1)

Buku besar dicek setelah setiap baris yang memindahkan uang.

| ID | Alur | Diharapkan |
|---|---|---|
| R1 | Unlock: PIN salah lalu benar | `Wrong PIN. 4 tries left.`, lalu beranda |
| R2 | Transfer Raka → Sinta (atau BI-FAST) Rp10,000 sampai `Sent`, ketuk ganda `Send` | Satu Journal Entry, PIN pad sekali |
| R3 | Bayar tagihan (PLN token atau e-wallet) sampai `Paid` | Satu `bill_payment` |
| R4 | Minta uang Rp10,000 + bayar (H1-9) | Satu `internal_transfer` |
| R5 | Split Rp20,000 dengan Sinta (bagian Rp10,000) → Sinta bayar | Satu `internal_transfer` |
| R6 | Insights → detail kategori (H2-1) | Hanya baca, tanpa Journal Entry |
| R7 | Deposito `Mature now` `Pay out` bunga Rp0 (H3-1) | Satu `deposit_close` dua Posting |

## 6. Kompatibilitas: debug 0.9.0 (3534aeb) terhadap server 0.9.1

| ID | Langkah | Diharapkan |
|---|---|---|
| C1 | Masuk (Sign in + PIN) | Beranda |
| C2 | Transfer Rp10,000 sampai `Sent` | Satu Journal Entry, buku besar OK |
| C3 | Minta uang Rp5,000 | Kotak error `Ask for at least Rp10,000.` (H1-7) |
| C4 | Minta uang Rp10,000 | `Request sent` |
| C5 | Insights → detail kategori | Tidak crash, daftar tabungan (H2-E1) |

## 7. Keluar dari uji

READY FOR RELEASE bila: 0 blocker, 0 major, semua AC H91-1..5 lolos atau terverifikasi lewat test yang disebut, Q-02-F1..F5 tertutup, buku besar OK di setiap titik, tidak ada Posting 0, izin identik dengan 0.9.0, A1 dan A2 (dua kali) hijau.
