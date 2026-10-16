# Laporan E2E: regresi penuh ORVYN 0.8.1

QA, 16-17 Okt 2026 (sekitar 17.00-22.00 UTC, 00.00-05.00 WIB). Build: `main` 4f75da2, debug 0.8.1 (versionCode 11) di Galaxy Note10+ lewat `adb reverse tcp:8790`, dev server lokal port 8790, admin dev. Rencana: `test-plan-e2e.md`. Screenshot di `qa-shots/` (status bar sudah dipotong), render font 200% di `qa-shots/robo/` (hasil cek posisi di `qa-shots/robo/results.txt`, salinan test sementara di `qa-shots/robo/QaE2eFontTest.kt.txt`; file test sudah dihapus dari repo).

## Ringkasan

Inti uang di 0.8.1 kokoh. Setiap uang bergerak lewat Journal Entry dan buku besar dev selalu **Balanced** (755 → 1117 entry, dicek setelah setiap transaksi). Transfer ORVYN, BI-FAST, RTGS, QRIS, tagihan, kartu, kantong, minta uang, split, valas, deposito, reksa dana, SBN, dan emas semuanya berhasil dengan posting yang benar. Ketukan ganda tidak pernah mengirim uang dua kali. Batas harian, saldo kurang, PIN salah, dan penguncian bekerja di server.

Tapi permintaan tim bisnis "pastikan tidak ada defect" belum terpenuhi. Ada **1 blocker, 11 major, dan 27 minor**. Polanya sama dengan audit CX:
1. **Font 200% di HP kecil** mematahkan pembuatan PIN, keypad nominal, dan layar sukses (E2E-01). Di ukuran Note10+ semua alur masih bisa diselesaikan, tapi label dan nominal terpotong di tengah kata (E2E-10).
2. **Kegagalan yang diam.** Lima aksi gagal tanpa pesan saat offline, dan pencairan deposito Rp100 juta yang berhasil juga tanpa konfirmasi (E2E-09).
3. **Celah keamanan dan admin.** Ganti PIN dan ganti password tidak menghitung percobaan salah (E2E-02), dan Reset demo gagal total bila ada nasabah yang punya passkey (E2E-03).
4. **Hambatan di pendaftaran dan masuk.** NIK ganda dan PIN lemah baru ditolak di langkah terakhir (E2E-05), pesan PIN tidak cocok tidak pernah tampil (E2E-06), dan field password tertutup keyboard di layar Sign in (E2E-04).

Temuan minor lama F3, F4, dan F5 masih terbuka. F4 ternyata lebih parah di layar 640dp: sekarang ikut memblokir pembuatan PIN (lihat E2E-01).

## 1. Test otomatis

| Cek | Hasil |
|---|---|
| `cd server && npm test` | **92/92 lolos** |
| `./gradlew testDebugUnitTest assembleDebug --rerun-tasks` | **28/28 lolos** (Format 3, Hotfix 10, Qr 1, Screenshot 12, Widget 2), APK debug 0.8.1 terbentuk |
| Robolectric sementara, skala 2.0 dan 1.0, 360×640dp dan 411×869dp, terang/gelap (56 render) | Lihat E2E-01 dan E2E-10. File test dihapus lagi, `git status` hanya berisi folder fitur ini |
| Buku besar dev (`checkLedger`) | OK di awal, setelah setiap transaksi uji, dan di akhir (1117 entry) |
| Dry run Reset demo (dalam transaksi yang di-rollback) | Gagal, lihat E2E-03. Data dev utuh |

## 2. Cakupan per story

Keterangan: **Lolos**, **Sebagian** (sebagian AC gagal atau tidak teruji), **Gagal**, **Tidak teruji** (alasan disebut).

### Fase 1
| Story | Hasil | Catatan |
|---|---|---|
| S1.1 Data pendaftaran | Sebagian | OTP lewat notifikasi, data diri, umur, draft lolos. AC3: error NIK tidak terlihat setelah Continue (UI-17). AC4: NIK ganda baru ditolak di langkah 8 (E2E-05) |
| S1.2 Foto KTP dan selfie | Sebagian | "Skip this step" dan penolakan izin kamera lolos. Foto sungguhan tidak diuji (aturan HP). Selfie tanpa tombol kembali (E2E-29) |
| S1.3 Password dan PIN | Sebagian | Password, layar "We're checking your details", hash di DB lolos. AC2: PIN urut baru ditolak setelah diulang, dan pesan PIN tidak cocok tidak tampil (E2E-05, E2E-06) |
| S1.4 Petugas memproses | Lolos | Setuju Everyday membuat rekening dan kartu debit, HP berubah sendiri ke "Your account is ready". Tolak tanpa alasan diterima server (E2E-15) |
| S1.5 Masuk pertama | Lolos | HP lama keluar dan dapat pesan inbox. 5 password salah mengunci, unlock petugas memulihkan. Tawaran sidik jari tidak muncul karena HP tanpa biometrik (jalur "tanpa sidik jari") |
| S1.6 Masuk cepat, keluar otomatis | Lolos | "N tries left" lalu terkunci di percobaan ke-5. Keluar otomatis tepat setelah 5 menit diam. Label "Demo" tampil. AC4 (waktu buka < 2 detik) tidak diukur |
| S1.7 Beranda | Sebagian | Sapaan, tier, saldo, sembunyikan saldo (diingat setelah restart), tarik untuk refresh lolos. Total assets tidak ikut refresh (E2E-08). AC4 offline sebagian (UI-07) |
| S1.8 Banner | Sebagian | Geser dan detail promo lolos. Banner baru dari admin tidak muncul saat refresh (E2E-13). Carousel bergeser saat diketuk (E2E-33) |
| S1.9 Detail rekening | **Gagal** | Kartu rekening tidak bisa diketuk, tidak ada detail atau bagikan. Hanya salin nomor (E2E-11, UI-09) |
| S1.10 Mutasi | Sebagian | Grup tanggal, cari nama/catatan, filter masuk/keluar lolos. AC3: tidak ada filter tanggal dan jenis (E2E-14) |
| S1.11 Detail dan bukti | Lolos | Detail, salin referensi, share sheet terbuka (ditutup tanpa memilih aplikasi). Tampilan "Reversed" di HP tidak dicek |
| S1.12 Transfer ORVYN | Lolos | Rekening sendiri/tak dikenal ditolak, saldo kurang, ketukan ganda terkirim sekali, push dua pihak, ledger OK. F3 masih ada (E2E-36) |
| S1.13 Bank lain | Lolos | BI-FAST Rp2,500 ke External Clearing + Fee Income. Nama konsisten. Pilihan BI-FAST/RTGS di atas Rp100 juta, RTGS Rp25,000 |
| S1.14 Penerima favorit | Lolos | Simpan, nama panggilan, cari nama/nomor, terakhir dikirimi. Hapus tidak dijalankan (UI-16). Pencarian kosong tanpa pesan (E2E-26) |
| S1.15 QRIS bayar | Sebagian | Kamera dan galeri **tidak diuji di HP** (aturan kamera, galeri berisi foto pribadi). Server lewat API: scan, bayar, PIN salah, idempotensi, limit, Merchant Settlement lolos. Layar nominal QRIS tidak bisa dicapai tanpa scan |
| S1.16 QR-ku | Sebagian | QR tampil, kecerahan 1.0 lalu kembali. AC1: tidak ada nominal opsional (E2E-17). AC2 butuh scan, tidak diuji |
| S1.17 Merchant admin | Sebagian | Daftar dan halaman merchant ada. Unduh/layar penuh tidak dijalankan |
| S1.18 Token PLN | Lolos | Token 4-4-4-4-4, salin, meter tersimpan |
| S1.19 PLN dan BPJS | Lolos | Bayar, "already paid", nomor tersimpan. Periode tampil "2026-10" (E2E-23) |
| S1.20 Pulsa | Sebagian | Telkomsel terdeteksi, prefix tak dikenal ditolak. AC2: nomor sendiri tidak terisi (E2E-18) |
| S1.21 E-wallet | Lolos | Nama dummy, biaya Rp1,000, minimum tidak dijelaskan (E2E-27) |
| S1.22 Biller admin | Tidak teruji | Tidak ada halaman biller (sesuai PROGRESS) |
| S1.23 Melihat kartu | Lolos | Detail dengan PIN, screenshot diblokir (screencap gagal), sembunyi setelah 30 detik |
| S1.24 Kendali kartu | Lolos | Freeze, online off, limit 1 juta: simulasi admin ditolak + push. Status switch tidak terbaca TalkBack (E2E-07) |
| S1.25 Tagihan kartu kredit | Lolos | Bayar 100k, sisa limit naik, minimum berubah, transaksi kartu terpisah. Gagal offline tanpa pesan (E2E-09) |
| S1.26 Simulasi transaksi kartu | Lolos | Ikut freeze, switch, limit; push berhasil/ditolak |
| S1.27 Notifikasi dan inbox | Sebagian | Push masuk, inbox, badge, tautan ke detail lolos. AC2/AC5 tidak bisa diuji di Android 12. AC4 (ketuk push) tidak diuji karena butuh panel notifikasi |
| S1.28 Push dan pengumuman | Lolos | Satu nasabah dengan preview, push tampil "ORVYN" di layar terkunci (visibility PRIVATE), pengumuman tampil sekali |
| S1.29 Tema | Lolos | Terang/gelap langsung berlaku. Format tanggal Inggris kecuali E2E-23 |
| S1.30 Keamanan akun | Sebagian | Ganti PIN/password dan keluar dengan konfirmasi lolos. Tidak ada batas percobaan (E2E-02). Biometrik tidak ada di HP |
| S1.31 Bantuan dan privasi | Sebagian | FAQ per kategori dan pencarian lolos. Privasi tidak lengkap (E2E-24). Versi di footer Profil |
| S1.32 Persona | Lolos | QA Persona dari Sinta: Rp15,168,500, 1 kantong, 2 anggaran, pesan untuk penguji. Nomor ganda ditolak. Dummy dibatasi 20 |
| S1.33 Kelola nasabah | Sebagian | Cari nama/nomor/rekening, tier, lock/unlock lolos. Ubah data diri tidak ditemukan di halaman admin. "View as customer" hanya riwayat |
| S1.34 Saldo | Lolos | Inflow realistis + push, adjustment tanpa alasan ditolak, minus ditolak |
| S1.35 Transaksi dan pembatalan | Lolos | Pembatalan, kedua kali ditolak, minus ditolak dengan rekening dan kekurangan. Filter jenis kurang lengkap (E2E-34) |
| S1.36 Konten | Sebagian | Tambah/hapus banner dan FAQ lolos. AC3 gagal (E2E-13). Tidak ada tanggal mulai dan urutan banner (E2E-34) |
| S1.37 Pengaturan | Sebagian | Limit, biaya, kurs, toggle fitur (Pockets dan Requests hilang lalu kembali) lolos. Maintenance hanya tampil saat app dibuka ulang (E2E-21) |
| S1.38 Petugas, audit, reset | Sebagian | Tambah petugas, peran ditegakkan (operator 403 di settings), nonaktif mengakhiri sesi, audit dengan filter lolos. Reset demo gagal bila ada passkey (E2E-03). Password awal di URL (E2E-16) |

### Fase 2
| Story | Hasil | Catatan |
|---|---|---|
| S2.0 Push | Lolos | Token push hilang saat keluar. Lock screen "ORVYN" |
| S2.1 Kantong | Lolos | Buat, gambar, target, ganti nama, tutup dengan konfirmasi dan tawaran pindah saldo |
| S2.2 Pindah uang | Lolos | Tanpa PIN, ketukan ganda sekali, "You have Rp100,000 to move.", "Goal reached". Judul riwayat membingungkan (UI-36) |
| S2.3 Tabung otomatis | Lolos | Round-up: belanja kartu Rp23,000 → Rp7,000 masuk kantong dalam 1 menit, ledger OK. Payday/weekly hanya lewat test server |
| S2.4 Jadwal | Lolos | Bulanan, PIN sekali, job 07:30 WIB (simulasi) dijalankan dua kali → terkirim sekali |
| S2.5 Kelola jadwal | Lolos | Pause/resume/hapus dengan konfirmasi, "Last one sent", tampil di admin. Resume offline diam (E2E-09) |
| S2.6 Minta uang | Sebagian | Buat (ketuk ganda sekali), bayar, tolak, batal, push. Kartu inbox tanpa Pay/Decline dan Pay langsung ke PIN tanpa review (E2E-20). AC6 tidak diuji |
| S2.7 Split | Lolos | Pembulatan ke pembuat, ubah bagian, status per orang, total dari riwayat |
| S2.8 Kategori | Lolos | Ganti kategori. Gagal offline diam (E2E-09) |
| S2.9 Insight | Lolos | Grafik 6 bulan dengan ringkasan TalkBack, persen vs bulan lalu, ketuk kategori |
| S2.10 Anggaran | Lolos | 80% → push dan inbox. Hapus tanpa konfirmasi (UI-16) |
| S2.11 Kartu insight | Lolos | |
| S2.12 Obrolan Vyna | Lolos | 4 pesan: saldo benar, Bahasa Indonesia dibalas Bahasa Indonesia, topik luar ditolak sopan, percakapan tetap setelah pindah layar |
| S2.13 Data nasabah | Lolos | Makanan September Rp980,000 = hasil `spending()` di server |
| S2.14 Siapkan transfer | Lolos | Kartu Review → layar review terisi, tetap PIN. Back dari review tidak langsung ke Vyna (E2E-30) |
| S2.15 Suara | Tidak teruji | Tombol mikrofon tidak muncul (HP tanpa pengenal suara on-device). Sesuai AC1 |
| S2.16 Biaya Vyna | Lolos | Batas 4 → pesan batas tanpa memanggil AI. Dashboard menghitung pesan dan token. Batas dikembalikan ke 30 |
| S2.17 E-statement | Sebagian | PDF dibuat di HP dan share sheet terbuka. Isi PDF tidak bisa dibaca (tidak ada penampil PDF di server QA) |
| S2.18 Widget | Tidak teruji | Butuh launcher (aplikasi lain). Ada WidgetTest unit |
| S2.19 Admin fase 2 | Sebagian | Kantong, jadwal, dan permintaan tampil di admin. Tidak ada toggle widget (E2E-22) |

### Fase 3
| Story | Hasil | Catatan |
|---|---|---|
| S3.1 Rekening valas | Lolos | JPY tanpa desimal, USD 2 desimal. Tutup tanpa konfirmasi (UI-16) |
| S3.2 Tukar valas | Lolos | Kurs Prestige, JE dengan FX Position, perubahan kurs tercatat di audit. Format kurs JPY membingungkan (E2E-23) |
| S3.3 Buka deposito | Lolos | Bunga, pajak, bersih, tanggal sebelum PIN. F3 melewati tenor (E2E-36) |
| S3.4 Jatuh tempo | Lolos | Mature now: payout Rp3,836 dan rollover Rp15,343 (cocok dengan hitungan), pesan inbox. Cair awal benar, tapi tanpa konfirmasi dan diam saat offline (E2E-09) |
| S3.5 Profil risiko | Lolos | Kuis 5 soal, bisa diulang |
| S3.6 Reksa dana | Sebagian | Beli 100k, jual 50k, grafik. Detail holding tanpa unit (E2E-19) |
| S3.7 SBN | Lolos | Kelipatan 1 juta ditegakkan, kupon setelah pajak terlihat di riwayat |
| S3.8 Emas | Sebagian | Beli 50k. Tidak bisa beli dalam gram (E2E-19) |
| S3.9 Wealth | Sebagian | Total, komposisi, kewajiban. Baris tabungan tidak bisa diketuk, label TalkBack "Gold 0%" vs "Less than 1%" (E2E-31) |
| S3.10 Prestige | Lolos | Ganti tier di admin → badge kuning dan Giallo, limit 1 miliar |
| S3.11 RM | Lolos | Kartu RM di beranda dan profil, monogram. Call/Message tidak diketuk (membuka aplikasi lain) |
| S3.12 Passkey | Sebagian | Sampai sheet Google; berhenti di "Set up screen lock". Tombol tidak ditekan, sheet ditutup dengan Back |
| S3.13 README | Tidak teruji | Di luar lingkup uji aplikasi |

### Hotfix 0.8.1
| Story | Hasil | Catatan |
|---|---|---|
| H1 | Lolos | `17081990` menjadi `17/08/1990` di pendaftaran baru |
| H2 | Lolos | Admin "Date of birth 1990-08-17" |
| H3 | Lolos | Chip 1M membuka deposito Rp1,000,000 |
| H4 | Lolos | Fund 100k, Bond 1M-10M, kelipatan 1 juta |
| H5 | Lolos | Transfer, e-wallet, kantong, minta uang, split, anggaran. QRIS tidak bisa dicapai di HP |

## 3. Temuan

### Blocker

#### E2E-01 (blocker, IT, desain CX): font 200% di layar 360×640dp: PIN tidak bisa dibuat, nominal tidak bisa diketik, tombol Done hilang
Lanjutan F4, sekarang lebih parah.

**Langkah:** render Robolectric skala 2.0 di 360×640dp (ukuran HP Android kecil yang umum), terang dan gelap.

**Diharapkan:** semua tombol bisa dijangkau (MASTER: tahan 200%).

**Aktual:**
- **Create PIN** (pendaftaran dan masuk pertama): baris `0` dan hapus tidak ter-render (bounds 0). PIN yang memuat angka 0 tidak bisa dibuat, dan salah ketik tidak bisa dihapus. `qa-shots/robo/create-pin-w360dp-x2.0-light.png`
- **Nominal transfer**: baris `000`, `0`, hapus terpotong oleh Continue. `qa-shots/robo/transfer-amount-w360dp-x2.0-light.png`
- **Sukses transfer terjadwal**: tombol Done tidak ada di layar. `qa-shots/robo/success-scheduled-w360dp-x2.0-light.png`
- **Unlock**: pesan "Wrong PIN. 2 tries left." terpotong setengah baris, "Not you?" terpotong, "Demo" hilang. `qa-shots/robo/unlock-w360dp-x2.0-light.png`

Di 411dp (Note10+) keempat layar masih utuh. Ini mengonfirmasi UI-01 dan UI-02.

### Major

#### E2E-02 (major, IT): ganti PIN dan ganti password tidak membatasi tebakan
**Langkah:** Profile → Change PIN → masukkan PIN lama salah → PIN baru dua kali.
**Diharapkan:** salah ke-5 mengunci nasabah, sama seperti layar Unlock dan pembayaran (S1.6 AC2).
**Aktual:** "That's not your current PIN." tanpa sisa percobaan, dan `failed_attempts` tetap 0. `POST /api/me/pin` dan `/api/me/password` (`server/src/api.ts`) hanya membandingkan hash tanpa menghitung. Orang yang memegang HP yang terbuka bisa menebak PIN tanpa batas. `qa-shots/30-change-pin-wrong-current.png`

#### E2E-03 (major, IT): Reset demo gagal bila ada nasabah yang punya passkey
**Langkah:** nasabah membuat passkey (S3.12) → Super Admin → Reset demo → RESET + password.
**Diharapkan:** semua data nasabah terhapus (S1.38 AC4).
**Aktual:** route `/admin/reset` (`server/src/admin-system.ts`) tidak menghapus tabel `passkeys`, jadi `delete from customers` melanggar `passkeys_customer_id_fkey` dan seluruh reset batal (500). Dibuktikan di DB dev dengan satu passkey palsu di dalam transaksi yang di-rollback: `ERROR: update or delete on table "customers" violates foreign key constraint "passkeys_customer_id_fkey"`. Data dev tidak berubah. Reset sungguhan tidak dijalankan.

#### E2E-04 (major, CX + IT): layar Sign in: field password dan pesan error tertutup keyboard
**Langkah:** Sign in → ketik nomor HP (keyboard terbuka).
**Diharapkan:** field password terlihat dan bisa diketuk, dan pesan error terbaca.
**Aktual:** area field yang bisa digulir mengecil sampai hanya label "Password" yang terlihat di atas tombol Sign in. Field-nya tersembunyi, dan tombol Done di keyboard tidak pindah ke password. Setelah password salah, field berwarna merah tapi "That phone number and password don't match." baru terlihat setelah keyboard ditutup. Ini menimpa setiap penguji di masuk pertama. Di font 200% (360dp) field password juga tersembunyi tanpa keyboard (`qa-shots/robo/sign-in-w360dp-x2.0-light.png`). Screenshot: `qa-shots/39-signin-password-field-hidden-by-keyboard.png`, `qa-shots/06-signin-error-hidden-by-keyboard.png`

#### E2E-05 (major, PO + IT): NIK ganda dan PIN lemah baru ditolak di langkah terakhir pendaftaran
**Langkah:** pendaftaran dengan NIK milik pendaftaran lain (`3173010101900004`), atau PIN `123456`, sampai "Enter it again".
**Diharapkan:** ditolak saat mengisi field itu (S1.1 AC4, S1.3 AC2), dengan jalan untuk memperbaikinya.
**Aktual:** keduanya diterima sampai langkah 8, lalu server menolak di layar "Enter it again". Pesannya jelas, tapi nasabah harus menekan Back 5 kali untuk kembali ke About you dan mengetik ulang password. Untuk PIN lemah tidak ada jalan selain mengetik PIN lain, yang lalu dianggap tidak cocok (E2E-06). `qa-shots/03-weak-pin-after-confirm.png`, `qa-shots/05-dup-nik-at-last-step.png`

#### E2E-06 (major, IT): pesan "Those PINs didn't match" tidak pernah tampil di pendaftaran
**Langkah:** pendaftaran → Create your PIN `482915` → Enter it again `482916`.
**Diharapkan:** kembali ke Create dengan "Those PINs didn't match. Start again."
**Aktual:** kembali diam-diam ke "Create your PIN" tanpa pesan. `error` milik langkah PinAgain di `RegisterFlow.kt`, sedangkan langkah Pin dipanggil dengan `null`. Nasabah mengira ketukannya hilang. `qa-shots/04-pin-mismatch-silent.png`

#### E2E-07 (major, IT): status sakelar kartu tidak terbaca TalkBack
**Langkah:** Cards → `uiautomator dump` pada baris Freeze card, Online, Overseas, Contactless.
**Diharapkan:** node `checkable` dengan `checked` true/false ("Freeze card, switch, on").
**Aktual:** baris hanya `clickable` dengan `Role.Switch` (`CardsScreen.kt` `SwitchRow`: `clickable(role = Role.Switch)` + `Switch(onCheckedChange = null)`), jadi tidak ada state. Pengguna TalkBack tidak tahu apakah kartunya sudah dibekukan. Pola yang sama perlu dicek di sakelar lain.

#### E2E-08 (major, IT): Total assets di beranda tidak ikut refresh
**Langkah:** nasabah baru (Rp0) → admin mengirim gaji Rp5,000,000 → tarik beranda untuk refresh.
**Diharapkan:** Total balance dan Total assets sama-sama Rp5,000,000.
**Aktual:** Total balance Rp5,000,000, tapi kartu Wealth tetap "Total assets Rp0". Baru benar setelah membuka layar Wealth. `WealthCard` hanya memuat ulang saat `DataVersion` berubah (transaksi dari app), bukan saat refresh atau uang masuk. Dua angka uang yang bertentangan di layar yang sama. Bukti: dump UI pukul 03:31 WIB (Total balance `Rp5,000,000`, Total assets `Rp0`).

#### E2E-09 (major, IT): aksi gagal diam saat offline, dan pencairan deposito berhasil tanpa konfirmasi
Mengonfirmasi UI-04 di HP (offline lewat `adb reverse --remove`):

| Aksi | Aktual | Bukti |
|---|---|---|
| Scheduled → Resume | Tetap "paused", tanpa pesan | `qa-shots/21-scheduled-resume-offline-silent.png` |
| Ganti kategori transaksi | Sheet tertutup, kategori tetap, tanpa pesan | `qa-shots/44-category-change-offline-silent.png` |
| Bayar tagihan kartu kredit setelah PIN | Kembali ke langkah pilih nominal tanpa pesan | `qa-shots/48-cardpay-offline-no-message.png` |
| Cair awal deposito setelah PIN | Kembali ke daftar deposito tanpa pesan | `qa-shots/51-deposit-withdraw-offline.png` |
| Cair awal deposito **berhasil** (online) | Juga kembali ke daftar tanpa layar sukses. Rp100,015,343 masuk, tapi nasabah tidak diberi tahu, dan tampilannya sama dengan kasus gagal | dump UI |

**Diharapkan:** setiap kegagalan menampilkan pesan (offline: "You're offline. Check your connection and try again."), dan pencairan menampilkan layar sukses seperti transaksi lain.

#### E2E-10 (major, IT, desain CX): font 200% di ukuran Note10+ (411dp): label dan nominal patah
**Aktual (render 2.0):**
- Aksi cepat beranda patah di tengah kata: "Transf/er", "Requ/est", "Top/up" (411dp dan 360dp).
- Saldo hero di 360dp patah di tengah angka: "Rp834,912,39" / "8". Sapaan "Good mornin/g, QA".
- Baris transaksi: judul jadi satu-dua huruf per baris karena nominal tidak dibatasi.
- Tombol "Sign in with a passkey" terpotong menjadi "Sign in with a".

Bukti: `qa-shots/robo/home-w411dp-x2.0-dark.png`, `home-w360dp-x2.0-light.png`, `list-rows-w360dp-x2.0-light.png`, `sign-in-w360dp-x2.0-light.png`. Ini mengonfirmasi UI-03 (a), (b), (e), (f). Saldo yang terbaca salah adalah risiko serius di aplikasi bank.

#### E2E-11 (major, PO + IT): S1.9 detail rekening tidak dibangun
**Langkah:** beranda → ketuk kartu ORVYN Everyday.
**Diharapkan:** detail (nama, nomor, saldo, tanggal dibuka) dan tombol bagikan (S1.9 AC1, AC3).
**Aktual:** tidak terjadi apa-apa. Hanya ikon salin ("Account number copied"). Mengonfirmasi UI-09.

#### E2E-12 (major, CX + IT): layar sukses tanpa "Share receipt" dan tanpa tanggal
**Langkah:** selesaikan transfer, QRIS, tagihan, atau bayar permintaan.
**Diharapkan:** bukti bisa dibagikan langsung (MASTER §5), dan ada tanggal/jam.
**Aktual:** hanya "Save … as a recipient" dan "Done". Bukti hanya bisa dibagikan dari riwayat. `qa-shots/12-transfer-success-no-share.png`. Mengonfirmasi UI-08.

### Minor

| ID | Pemilik | Temuan | Langkah singkat → aktual (diharapkan) | Bukti |
|---|---|---|---|---|
| E2E-13 | IT | Banner dan kartu insight dimuat sekali saja | Banner baru di admin → refresh beranda → tidak muncul sampai app dibuka ulang (S1.36 AC3). Setelah offline lalu online, banner dan insight hilang sampai restart. Saat offline judul "WEALTH" tampil tanpa isi | `qa-shots/54-home-offline.png` |
| E2E-14 | PO | Filter riwayat tanpa rentang tanggal dan jenis | Hanya All/Money in/Money out (S1.10 AC3). = UI-34 | dump UI |
| E2E-15 | IT | Server menerima penolakan pendaftaran tanpa alasan | `POST /admin/applications/:id/reject` dengan `reason=` → status rejected, alasan kosong (S1.4 AC4). Hanya `required` di HTML yang menjaga | DB `registrations.reason` kosong |
| E2E-16 | IT | Password awal petugas ada di URL | Tambah officer → redirect `?done=…Starting password: …` masuk riwayat browser dan log. Seharusnya ditampilkan tanpa query string | header Location |
| E2E-17 | IT | QR-ku tanpa nominal opsional | S1.16 AC1. Server sudah mendukung `?amount=`, layar tidak punya field | `qa-shots/22-my-qr.png` |
| E2E-18 | IT | Pulsa: nomor sendiri tidak terisi, operator baru tampil setelah Continue | S1.20 AC1-2 | `qa-shots/27-mobile-no-own-number.png` |
| E2E-19 | IT | Investasi: emas tidak bisa dibeli dalam gram; detail Fund/Gold "You hold" tanpa unit/gram | S3.8 AC2-3, S3.6 AC5. Server sudah menerima `grams` | dump UI |
| E2E-20 | PO | Minta uang: kartu inbox tanpa Pay/Decline, Pay langsung ke PIN tanpa layar review | S2.6 AC2-3. PO putuskan apakah AC diubah atau dibangun | dump UI |
| E2E-21 | IT | Maintenance tidak mengganti layar saat app terbuka | Maintenance on → app yang sedang terbuka tetap normal, transfer ditolak dengan pesan inline. Layar "Back soon" hanya saat dibuka ulang (S1.37 AC4) | `qa-shots/37-maintenance-in-flow.png`, `38-maintenance-screen.png` |
| E2E-22 | PO + IT | Tidak ada sakelar Widget di Settings | S2.19 AC3 menyebut widget | admin Settings |
| E2E-23 | CX | Copy dan format | "Bill for 2026-10" (S1.29 AC2: "Oct 2026"); review RTGS "Arrives: Within the hour, for amounts above Rp100,000,000"; eyebrow "YOUR INVESTMENTS · RP25,971,710"; "-1.30%" memakai tanda hubung ASCII, bukan U+2212; kurs JPY "Rp108.025" mudah dibaca Rp108.025 ribu; tombol "Review" di valas langsung ke PIN; subjudul Sign in "the ORVYN team sent you" salah untuk nasabah yang mendaftar sendiri; dialog "Cancel" vs "Cancel request"; nasabah yang dikunci petugas melihat "locked after too many wrong tries"; "Welcome back" tanpa nama setelah idle, "Welcome back, QA" setelah restart | `qa-shots/25-pln-bill-period-iso.png`, `49-fx-jpy-rate-format.png`, `53-investments-minus-and-RP.png`, `42-cancel-request-dialog.png` |
| E2E-24 | PO + CX | Halaman Privacy tidak lengkap | Kamera disebut "only while the QRIS scanner is open", padahal pendaftaran juga memakai kamera. Mikrofon Vyna tidak disebut (PRD 7.4) | dump UI |
| E2E-25 | IT | Error password lama tampil di bawah field password baru | Change password dengan password lama salah | `qa-shots/31-password-error-under-new-field.png` |
| E2E-26 | CX | Pencarian penerima tanpa hasil: layar kosong | Ketik "zzz" → tidak ada pesan "No match" | `qa-shots/14-recipient-search-no-results.png` |
| E2E-27 | CX | E-wallet di bawah minimum tidak menjelaskan minimum | Rp5,000 → tombol "Choose what to pay" nonaktif tanpa "Minimum Rp10,000" | dump UI |
| E2E-28 | IT | Setelah petugas membuka kunci, layar Unlock tetap "locked" | Tidak ada "Try again". Harus tutup paksa app atau "Not you?" (yang melupakan HP) | `qa-shots/35-unlock-locked.png` |
| E2E-29 | CX | Langkah selfie tanpa panah kembali | KTP punya Back, selfie tidak (`back = false`) | dump UI |
| E2E-30 | IT | Back dari review hasil Vyna melewati layar nominal dan penerima | Perlu 2-3 kali Back untuk kembali ke Vyna | dump UI |
| E2E-31 | IT | Wealth: baris "Savings and pockets" tidak bisa diketuk; label TalkBack "Gold 0%" vs teks "Less than 1%" | S3.9 AC4 | dump UI |
| E2E-32 | IT | Label aksesibilitas | Ikon bagikan e-statement hanya "Share" (tanpa bulan); baris inbox informatif diumumkan "disabled"; saldo tersembunyi dibaca "Rp••••••••" (UI-32) | dump UI |
| E2E-33 | CX | Carousel bergeser saat diketuk | Ketuk banner "QA banner test" → terbuka detail banner berikutnya ("Scan. Pay. Done.") | `qa-shots/55-banner-detail.png` |
| E2E-34 | PO | Admin: filter transaksi tanpa jenis pocket/valas/deposito/investasi; banner tanpa tanggal mulai, urutan, dan edit | S1.35 AC1, S1.36 AC1 | admin |
| E2E-35 | CX | Ganti PIN baru memeriksa PIN lama setelah PIN baru diketik dua kali | 18 digit sebelum tahu PIN lama salah | `qa-shots/30-change-pin-wrong-current.png` |
| E2E-36 (= F3) | IT | Ketukan ganda terbawa ke layar berikutnya, **masih ada** | "Send Rp50,000" dua kali → PIN pad "1 of 6 digits entered". Deposito: Next dua kali → tenor terlewati | `qa-shots/11-F3-double-tap-into-pin.png`, `52-F3-deposit-tenor-skipped.png` |
| E2E-37 (= F5) | IT | Review transfer tetap "Loading" setelah gagal offline, **masih ada** | BI-FAST, offline saat PIN, online lagi → rincian "Loading", Send nonaktif. Harus kembali ke nominal | `qa-shots/16-F5-review-stuck-after-online.png` |
| E2E-38 | IT | Kegagalan muat tanpa "Try again" | Cards dan Inbox offline: "Couldn't load…" tanpa tombol. Setelah online tetap kosong sampai pindah layar (UI-05) | `qa-shots/56-cards-offline-no-retry.png` |
| E2E-39 | IT | PIN pad bisa direkam layar | `screencap` berhasil di semua layar PIN (detail kartu benar diblokir). = UI-14 | `qa-shots/26-bpjs-wrong-pin.png` |

F4 (keypad terpotong 360dp/200%) **masih ada dan diperluas** menjadi E2E-01.

## 4. Konfirmasi audit UI (CX)

| ID | Status | Bukti |
|---|---|---|
| UI-01 | **Dikonfirmasi** di 360×640dp: Create PIN kehilangan baris 0/hapus, slot error Unlock terpotong. Di 411dp aman | E2E-01 |
| UI-02 | **Dikonfirmasi**: keypad nominal (bukan tombol Continue) dan tombol Done di layar sukses keluar layar di 360dp | E2E-01 |
| UI-03 | **Dikonfirmasi** (a) saldo patah, (b) aksi cepat patah, (e) ListRow, (f) label tombol terpotong. (c) bottom nav dan (d) banner/kantong tidak dirender | E2E-10 |
| UI-04 | **Dikonfirmasi** untuk jadwal, kategori, bayar kartu, cair awal deposito, review transfer. Hapus penerima, tutup valas, passkey tidak dicoba offline | E2E-09, E2E-37 |
| UI-05 | **Dikonfirmasi** (Cards, Inbox) | E2E-38 |
| UI-06 | **Sebagian**. PIN salah di BPJS dan deposito tampil benar di slot PIN. Error non-PIN di QRIS/bills tidak bisa dipicu di HP. Bayar kartu: error non-PIN kembali ke langkah 0 tanpa pesan (E2E-09). Status "sedang diproses" setelah 6 digit tidak tertangkap karena server lokal menjawab < 1 detik | |
| UI-07 | **Dikonfirmasi**: aksi cepat nonaktif terlihat sama (TalkBack "disabled"), banner tanpa ikon, hanya jam. **Dibantah** soal posisi: saat offline banner promo dan insight tidak dimuat, jadi banner offline langsung di bawah aksi cepat | `qa-shots/54-home-offline.png` |
| UI-08 | **Dikonfirmasi** | E2E-12 |
| UI-09 | **Dikonfirmasi** | E2E-11 |
| UI-10 | **Dikonfirmasi** di dua tema: gelap, chip tidak dipilih biru pekat dan dipilih biru muda; terang, chip tidak dipilih nyaris hilang | `qa-shots/20-schedule-chips-dark.png`, `37-maintenance-in-flow.png`, `robo/transfer-amount-w411dp-x2.0-dark.png` |
| UI-11 | Tidak diukur (perlu pengukur kontras) | |
| UI-12 | **Dikonfirmasi** untuk nominal valas ("JPY 1,000" tampil Roboto) | `qa-shots/49-fx-jpy-rate-format.png` |
| UI-13 | Tidak dicek (animasi) | |
| UI-14 | **Dikonfirmasi** | E2E-39 |
| UI-15 | **Sebagian**: carousel di antara aksi cepat dan rekening, bergeser sendiri, ketukan meleset (E2E-33). Posisi tetap keputusan tim bisnis | |
| UI-16 | **Dikonfirmasi**: hapus anggaran dan tutup valas langsung jalan tanpa dialog. "Remove from saved" tepat di bawah Save | `qa-shots/46-budget-remove-next-to-save.png`, `13-recipient-sheet-remove-next-to-save.png` |
| UI-17 | **Dikonfirmasi**: Continue dengan NIK 15 digit tidak menggulir ke error | `qa-shots/02-ui17-continue-no-feedback.png` |
| UI-18 | Tidak bisa diuji (notifikasi aktif di Android 12) | |
| UI-19 | Tidak diuji (TalkBack tidak dinyalakan) | |
| UI-20 | Tidak bisa diuji (kamera ditolak sesuai aturan). Pesan izin kamera ada | `qa-shots/23-qris-camera-denied.png` |
| UI-22 | **Dikonfirmasi**: status permintaan hanya teks berwarna | `qa-shots/41-requests-status.png` |
| UI-26 | **Dikonfirmasi**: kolom "We buy"/"We sell" | dump UI |
| UI-27 | **Dikonfirmasi**: chip "1M/6M/1Y" di grafik reksa dana | dump UI |
| UI-29 | Tidak terlihat: empat jawaban Vyna tanpa markdown | `qa-shots/47-vyna-transfer-card.png` |
| UI-32 | **Dikonfirmasi** | E2E-32 |
| UI-33 | **Dikonfirmasi**: tidak ada "Forgot PIN?" | `qa-shots/34-unlock-1-try-left.png` |
| UI-34 | **Dikonfirmasi** | E2E-14 |
| UI-35 | **Sebagian**: keyboard sistem menutupi keypad saat mengisi catatan, helper terpotong. Tidak ada dua keyboard bersamaan | `qa-shots/40-request-note-two-keyboards.png` |
| UI-36 | **Dikonfirmasi**: riwayat kantong "ORVYN Everyday · Pocket" | dump UI |
| UI-44 | **Dikonfirmasi**: "Back soon" + "ORVYN is being updated" + pesan server bertumpuk | `qa-shots/38-maintenance-screen.png` |
| UI-45 | **Dikonfirmasi**: "PIN changed" hanya judul dan tombol | dump UI |
| UI-21, 23-25, 28, 30, 31, 37-43, 46 | Tidak dicek di sesi ini | |

## 5. Catatan

- **Data uji di dev:** nasabah baru QA EndToEnd `081290020001` (password sekarang `orvyn5678`, PIN `590317`, Prestige, kantong "QA Trip" Rp107,000, jadwal sudah dihapus); QA Persona `081290020002`; 22 nasabah dummy; pendaftaran QA Dob Dua ditolak; petugas `qa.content@orvyn.test` dan `qa.operator@orvyn.test` (nonaktif). Raka, Sinta, dan Hendra punya transaksi uji (transfer, minta uang, split, deposito 1 juta Hendra, reksa dana, emas, bayar kartu). Buku besar dev OK (1117 entry).
- **Pengaturan dev dikembalikan:** semua fitur menyala, maintenance off, batas Vyna 30, kurs USD kembali ke 16,420, banner uji dihapus.
- **Vyna:** 4 pertanyaan (sekitar 20 ribu token). Satu panggilan batas tidak memanggil AI.
- **Jadwal transfer:** job 07:00 dijalankan langsung lewat `runScheduledTransfers` dengan waktu simulasi 07:30 WIB, karena uji berjalan pukul 03-05 WIB.
- **Insiden di HP (dilaporkan apa adanya):** dua kali Back terkirim saat ORVYN berada di beranda yang tergulir (setelah layar sukses langsung kembali ke beranda), sehingga ORVYN tertutup ke launcher. Guard menghentikan semua input berikutnya, jadi tidak ada ketukan yang sampai ke aplikasi lain. Saya lalu membuka ORVYN lagi dengan `am start` dan memperketat helper Back. Satu ketukan salah di y=0 terjadi saat ORVYN di depan (tanpa efek). Kamera tidak pernah memotret: izin ditolak di dialog yang dibuka ORVYN. Share sheet dan sheet passkey Google hanya ditutup dengan Back. Pengaturan HP tidak disentuh.
- **Akhir sesi:** release 0.8.1 dari `releases/orvyn.apk` terpasang lagi (versionCode 11). Sesi ORVYN pemilik ikut keluar karena uninstall. `adb reverse` sudah dihapus, dev server saya sudah dihentikan (server produksi tidak disentuh), dan semua fitur menyala. Saat sesi dimulai, ada dialog Google Play Protect dan jendela "Application Error: id.orvyn.app" (dari 0.8.0) di HP. Keduanya bukan dibuka oleh uji ini dan tidak disentuh. Dialog crash hilang saat uninstall, sedangkan Play Protect mungkin muncul lagi setelah pemasangan ulang.

## 6. Putusan kualitas 0.8.1

Untuk uang, 0.8.1 layak dipakai demo. Tidak ada saldo yang salah di buku besar, tidak ada transaksi ganda, dan setiap aturan uang ditegakkan server. Tapi 0.8.1 **belum bebas defect**. Satu blocker aksesibilitas (E2E-01) membuat nasabah dengan font besar di HP kecil tidak bisa membuat PIN. Sebelas major menyentuh kepercayaan (kegagalan diam, angka aset yang bertentangan, celah tebak PIN, Reset demo yang rusak) dan hambatan masuk/daftar. Saran untuk orkestrator: E2E-01 sampai E2E-12 dan F3/F5 masuk rilis perbaikan 0.9.0 "quality pass" bersama UI-01..UI-08 dari audit CX. E2E-11, E2E-14, E2E-20, E2E-22, dan E2E-24 ke PO dulu, karena menyangkut AC yang belum dibangun atau perlu diubah.

**NOT READY** (sebagai klaim "tanpa defect"; tidak ada temuan yang membuat uang salah)
