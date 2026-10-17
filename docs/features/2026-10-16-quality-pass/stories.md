# User Stories ORVYN: Quality pass 0.9.0

Sumber: `prd.md` dan `requirement.md` di folder ini, temuan di `e2e-report.md` dan `ui-audit.md`, istilah dari `CONTEXT.md`. Desain: `design.md` di folder ini (ditulis CX). Copy dalam tanda kutip adalah copy final bahasa Inggris dari `ui-audit.md`. Bila tertulis "sesuai `design.md`", copy-nya ditulis CX.

**Peran:** Nasabah (Customer), calon nasabah, Petugas (Officer), Pemilik proyek. Sama dengan Fase 1–3.

**Prioritas:** **P0** = menutup blocker atau major, dikerjakan dan di-QA di tahap 1. **P1** = minor atau polish, tahap 2.

## Istilah uji

- **Uji 200%:** ukuran font 2,0 (render Robolectric atau `font_scale 2.0`) di **360×640dp** dan **411×869dp**, tema **terang dan gelap**. Empat render per layar.
- **Offline:** koneksi ke server diputus (di dev: `adb reverse --remove`).
- **TalkBack:** bila TalkBack asli tidak bisa dinyalakan di HP pemilik, AC TalkBack diverifikasi dengan `uiautomator dump` (label, `checkable`, `checked`, `expanded`, role) atau test semantics Compose.
- **Tidak bisa di HP:** AC yang ditandai begitu diverifikasi dengan test otomatis (Compose, Robolectric, atau server) dan review kode, sesuai `requirement.md`.

## Definisi selesai (berlaku untuk semua story)

1. Semua test server lolos, termasuk test baru untuk: cek NIK, batas percobaan Ganti PIN/password, Reset demo dengan passkey, penolakan tanpa alasan, password awal petugas, banner (tanggal mulai, urutan, edit), filter jenis admin, dan judul riwayat kantong.
2. Aplikasi ter-build sebagai **0.9.0 (versionCode 12)**. Daftar izin di APK sama persis dengan 0.8.1.
3. Uji 200% lolos untuk minimal: Unlock, Create PIN, PIN transaksi, nominal Transfer, nominal deposito, sukses Transfer, sukses transfer terjadwal, beranda (Everyday dan Prestige), Sign in, riwayat, detail rekening. Tidak ada teks yang terpotong, tidak ada tombol di luar layar.
4. Copy di aplikasi dan admin sama persis dengan `design.md`.
5. Buku besar dev Balanced di awal, setelah setiap transaksi uji, dan di akhir QA.
6. Server 0.9.0 tetap melayani APK 0.8.1 (smoke: masuk, transfer, bayar tagihan, Ganti PIN).
7. `design-system/MASTER.md` sudah diperbarui CX untuk token, chip, tipografi, dan transisi.

---

## A. Font besar

### Q-A1 Layar PIN tahan font besar (P0)
Temuan: E2E-01 (Create PIN, Unlock), UI-01. Komponen: PinPad, dipakai di semua layar PIN.

**Sebagai** nasabah yang memakai font besar, **saya ingin** semua tombol PIN pad dan pesannya selalu terlihat, **supaya** saya bisa membuat PIN, membuka aplikasi, dan menyetujui pembayaran.

1. Uji 200% pada Create PIN (pendaftaran dan masuk pertama), Unlock, dan satu PIN transaksi (Transfer): keempat baris keypad, termasuk `0` dan hapus, tampil utuh dan bisa diketuk.
2. Di 360×640dp dengan font 2,0, membuat PIN `204060` di Create PIN berhasil, dan satu ketukan hapus menghapus satu digit.
3. Di Unlock dengan font 2,0 di 360dp, pesan "Wrong PIN. 2 tries left." terbaca utuh (boleh dua baris), dan "Not you?" serta label "Demo" terlihat. Bila tidak muat, bagian atas bisa digulir. Keypad tetap menempel di bawah.
4. Di font 1,5 ke atas, judul tampil satu baris dan kalimat penjelas tidak tampil, tapi `uiautomator dump` menunjukkan kalimat itu tetap ada sebagai teks yang dibaca TalkBack.
5. Di font 1,0 di 411dp, tampilan layar PIN sama dengan 0.8.1 (screenshot test lama tetap cocok atau perbedaannya disetujui CX).

### Q-A2 Layar nominal tahan font besar (P0)
Temuan: E2E-01 (nominal Transfer), F4, UI-02. Komponen: pola layar nominal bersama.

**Sebagai** nasabah yang memakai font besar, **saya ingin** keypad nominal dan tombol kirim selalu terlihat, **supaya** saya bisa mengetik nominal dan melanjutkan.

1. Uji 200% pada nominal Transfer, QRIS (nominal diketik), bayar tagihan kartu "Another amount", minta uang, kantong (pindah uang), buka deposito, beli Fund, dan beli/jual valas: baris `000`, `0`, hapus, dan tombol utama tampil utuh dan bisa diketuk.
2. Di 360×640dp font 2,0, mengetik `50000` di nominal Transfer dengan keypad lalu mengetuk "Continue" membawa ke langkah berikutnya.
3. Nominal dan Quick Amount berada di area yang bisa digulir. Keypad dan tombol utama tetap menempel di bawah saat area itu digulir.
4. Quick Amount tetap membungkus dan terbaca utuh di font 2,0 (perilaku 0.8.1 tidak mundur).
5. Label tombol utama yang menyebut nominal ("Send Rp500,000") terbaca utuh di font 2,0, boleh dua baris.

### Q-A3 Layar sukses tahan font besar (P0)
Temuan: E2E-01 (Done di sukses terjadwal), UI-02 (layar sukses).

**Sebagai** nasabah yang memakai font besar, **saya ingin** tombol "Done" selalu bisa diketuk di layar sukses, **supaya** saya bisa keluar dengan benar setelah uang terkirim.

1. Uji 200% pada sukses Transfer dan sukses transfer terjadwal (5 baris rincian): "Done" tampil utuh di bawah layar, di atas navigasi sistem.
2. Bila rincian lebih panjang dari layar, rincian bisa digulir sampai baris terakhir, dan "Done" tetap menempel di bawah.
3. Tombol sekunder ("Save {nama} as a recipient", "Share receipt") juga terlihat dan bisa diketuk di font 2,0.
4. Hal yang sama berlaku untuk sukses QRIS, Bill Payment, bayar tagihan kartu, bayar Money Request, buka deposito, dan beli investasi (dicek dengan render atau di HP).

### Q-A4 Nominal dan saldo tidak pernah patah di tengah angka (P0)
Temuan: E2E-10 (saldo hero), UI-03 (a). Komponen: AmountDisplay, saldo hero, nominal sukses.

**Sebagai** nasabah, **saya ingin** saldo dan nominal selalu terbaca utuh, **supaya** saya tidak salah membaca uang saya.

1. Uji 200% beranda Prestige dengan saldo Rp834,912,398: saldo tampil di satu baris (mengecil otomatis) atau membungkus hanya di koma ribuan. Tidak ada baris yang berisi sebagian kelompok angka, misalnya "8".
2. Hal yang sama berlaku untuk nominal di layar nominal (minimal 24sp sebelum membungkus) dan nominal di layar sukses.
3. Di font 1,0, ukuran saldo hero sama dengan 0.8.1.
4. Animasi hitung naik saldo tetap berjalan dan berakhir di angka yang tepat setelah ukuran mengecil.

### Q-A5 Beranda dan navigasi tahan font besar (P0)
Temuan: E2E-10 (aksi cepat, sapaan), UI-03 (b, c, d).

**Sebagai** nasabah yang memakai font besar, **saya ingin** label di beranda tidak terpotong di tengah kata, **supaya** saya tahu tombol apa yang saya ketuk.

1. Uji 200% beranda: label aksi cepat ("Transfer", "Request", "Top up", dan lainnya) tidak terpotong di tengah kata. Di font 1,5 ke atas, aksi cepat tampil sebagai grid dua baris dan label boleh dua baris.
2. Sapaan ("Good morning, {nama}") membungkus per kata, tidak di tengah kata.
3. Bottom nav: label "QRIS" tidak bertabrakan dengan tombol QRIS, dan semua label terbaca utuh di font 2,0.
4. Banner dan kartu kantong tidak memotong judul atau nama. Nama kantong 24 huruf terbaca utuh (boleh dua baris).
5. Di font 1,0, tata letak beranda sama dengan 0.8.1 kecuali urutan bagian (Q-G1).

### Q-A6 Baris daftar dan tombol tahan font besar (P0)
Temuan: E2E-10 (baris transaksi, tombol passkey), UI-03 (e, f). Komponen: ListRow, OrvynButton.

**Sebagai** nasabah yang memakai font besar, **saya ingin** nama di baris transaksi dan label tombol terbaca utuh, **supaya** saya tahu siapa penerima dan apa yang dilakukan tombol.

1. Uji 200% daftar riwayat: di font 1,5 ke atas, nominal pindah ke baris sendiri di bawah judul, dan judul seperti "Sinta Maharani" terbaca minimal satu kata utuh per baris.
2. Di font di bawah 1,5, nominal tetap di kanan seperti 0.8.1.
3. Tombol "Sign in with a passkey" di layar Sign in terbaca utuh di font 2,0 (tinggi tombol bertambah, boleh dua baris).
4. Tidak ada OrvynButton di layar mana pun yang memotong label di font 2,0 (dicek di layar pada definisi selesai poin 3).

---

## B. Tidak ada kegagalan yang diam

### Q-B1 Aksi yang gagal selalu memberi tahu (P0)
Temuan: E2E-09 (empat aksi offline), UI-04 (aksi).

**Sebagai** nasabah, **saya ingin** selalu diberi tahu bila aksi saya gagal, **supaya** saya tidak mengira sudah berhasil.

1. Offline, lakukan setiap aksi ini: lanjutkan jadwal (Resume), hentikan jadwal (Pause), hapus jadwal, ganti kategori transaksi, simpan penerima, hapus penerima tersimpan, ubah instruksi jatuh tempo deposito, tutup FX Account, hapus passkey. Setiap aksi menampilkan "You're offline. Check your connection and try again." dan tampilan tetap seperti sebelum aksi (status, kategori, daftar tidak berubah).
2. Bayar tagihan kartu kredit offline setelah PIN: aplikasi tetap di layar periksa dan pesan offline tampil di atas tombol "Pay" dengan ikon. Tidak kembali ke langkah pilih nominal.
3. Cair awal deposito offline setelah PIN: aplikasi tetap di layar periksa pencairan dengan pesan offline. Deposito masih ada di daftar.
4. Bila server menolak aksi (bukan offline), pesan server tampil: inline di bawah field bila ada field terkait, selain itu sebagai snackbar.
5. Setelah online lagi, mengulang aksi yang sama berhasil sekali, dan buku besar dev tetap Balanced.

### Q-B2 Layar sukses pencairan deposito lebih awal (P0)
Temuan: E2E-09 (pencairan berhasil tanpa konfirmasi).

**Sebagai** nasabah, **saya ingin** melihat konfirmasi setelah mencairkan deposito lebih awal, **supaya** saya yakin uangnya sudah masuk ke tabungan.

1. Cair awal deposito Rp1,000,000 dengan PIN benar: layar sukses tampil dengan nominal yang masuk ke tabungan, keterangan bahwa bunga hangus, dan baris "Date" (copy sesuai `design.md`).
2. Nominal di layar sukses sama dengan nominal Journal Entry pencairan di admin dan dengan kenaikan saldo tabungan.
3. "Done" kembali ke daftar deposito, dan deposito itu tidak ada lagi di daftar.
4. Layar sukses tidak bisa di-Back ke PIN.
5. Journal Entry pencairan sama dengan 0.8.1 (pokok dari Time Deposit ke Savings Account, tanpa Posting bunga), dan buku besar Balanced.

### Q-B3 Layar yang gagal memuat punya "Try again" (P0)
Temuan: E2E-38, UI-05, UI-04 (muat data). Komponen: MessageState.

**Sebagai** nasabah, **saya ingin** tombol "Try again" setiap kali layar gagal memuat, **supaya** saya tidak perlu keluar dan masuk lagi.

1. Buka offline: Cards, Inbox, riwayat, Requests, Wealth, valas, deposito, investasi, e-statement, Bantuan, Insights, kantong, Scheduled, layar opsi transfer, pilih penerima Transfer, Bills, QRIS (nominal), Passkeys. Setiap layar menampilkan pesan error dengan "Try again". Bila server tidak memberi pesan: "Couldn't load this. Check your connection and try again."
2. Setelah online lagi, mengetuk "Try again" memuat isi layar tanpa pindah layar.
3. Tidak ada layar yang menampilkan skeleton lebih dari 15 detik. Setelah itu tampil error dengan "Try again".
4. Passkeys yang gagal dimuat menampilkan error, bukan "No passkeys yet.".
5. Opsi transfer (BI-FAST/RTGS) yang gagal dimuat menampilkan error dengan "Try again", bukan tombol yang tidak pernah aktif.

### Q-B4 Layar periksa Transfer pulih setelah offline (P0)
Temuan: E2E-37 (= F5), UI-04 (TransferFlow).

**Sebagai** nasabah, **saya ingin** layar periksa Transfer bisa dicoba lagi setelah koneksi kembali, **supaya** saya tidak harus mengulang dari nominal.

1. Transfer BI-FAST, putuskan koneksi, masukkan PIN: pesan offline tampil di layar periksa.
2. Sambungkan lagi, ketuk tombol kirim: rincian dimuat ulang (tidak "Loading" selamanya), lalu PIN diminta, dan transfer terkirim sekali.
3. Bila rincian gagal dimuat saat layar periksa dibuka, layar menampilkan error dengan "Try again", dan tombol kirim nonaktif dengan label yang menjelaskan alasannya (sesuai `design.md`).
4. Journal Entry hanya bertambah satu untuk alur ini, dan buku besar Balanced.

### Q-B5 PIN pad memberi tahu saat sedang diproses (P0)
Temuan: UI-06. Komponen: PinPad.

**Sebagai** nasabah, **saya ingin** tahu bahwa PIN saya sedang diproses, dan tahu bedanya PIN salah dengan saldo kurang, **supaya** saya tidak mengetik ulang atau keluar saat uang sedang dikirim.

1. Setelah digit ke-6 di setiap layar PIN, enam titik tetap terisi, keypad nonaktif dan redup, dan teks status tampil di slot tetap tanpa menggeser tata letak: "Checking your PIN…" di Unlock, "Sending Rp500,000…" di Transfer Rp500,000, "Paying Rp25,000…" di pembayaran Rp25,000. (Server lambat disimulasikan di test otomatis.)
2. Selama status itu tampil, ketukan di keypad tidak menambah digit.
3. TalkBack membacakan teks status sekali (dicek di semantics: live region, satu pengumuman).
4. PIN salah: getar REJECT dan titik bergoyang, lalu "Wrong PIN. N tries left.".
5. Error selain PIN salah (misalnya saldo kurang di QRIS atau tagihan, atau melewati Daily Limit) tidak memakai getar REJECT dan tidak menggoyang titik. Aplikasi kembali ke layar periksa dengan pesan inline, misalnya "You're Rp120,000 short.".
6. Bila animasi dimatikan di HP, titik tidak berdenyut (diam terisi).

### Q-B6 Beranda offline yang jujur (P0)
Temuan: UI-07, UI-04 (cache yang gagal diperbarui).

**Sebagai** nasabah, **saya ingin** tahu bahwa saldo di beranda adalah data lama dan kenapa tombol tidak bisa dipakai, **supaya** saya tidak salah mengira saldo hari ini.

1. Offline, buka beranda: banner tepat di bawah hero dengan ikon wifi tercoret dan teks "You're offline. These balances are from {Yesterday, 21:40}." lalu "Sending money works again once you're back online.". Hari ditulis "Today", "Yesterday", atau tanggal.
2. Aksi cepat tampil redup (38%). Mengetuknya menampilkan snackbar "You're offline. Try again when you're connected.". TalkBack membaca "Transfer, unavailable while offline".
3. Bila pembaruan gagal karena hal lain (bukan offline) dan ada data cache, banner yang sama menampilkan "Couldn't refresh. Showing balances from {hari}, {jam}." dengan "Try again".
4. Setelah online dan tarik untuk refresh, banner hilang dan aksi cepat kembali normal.
5. Banner terbaca utuh di uji 200%, terang dan gelap.

### Q-B7 Tarik untuk refresh memperbarui seluruh beranda (P0)
Temuan: E2E-08, E2E-13.

**Sebagai** nasabah, **saya ingin** semua angka dan konten di beranda ikut diperbarui, **supaya** tidak ada dua angka yang saling bertentangan.

1. Nasabah dengan saldo Rp0. Petugas mengirim Simulated Inflow Rp5,000,000. Tarik beranda untuk refresh: Total balance dan "Total assets" di kartu Wealth sama-sama naik Rp5,000,000, dan Total assets sama dengan angka di layar Wealth.
2. Total assets juga diperbarui saat aplikasi kembali dari latar belakang ke beranda.
3. Petugas menambah banner baru yang aktif. Tarik beranda untuk refresh: banner baru tampil tanpa membuka ulang aplikasi (S1.36 AC3).
4. Offline lalu online: setelah tarik untuk refresh, banner dan kartu insight tampil lagi tanpa restart.
5. Bagian yang datanya kosong dan tidak punya cache (misalnya Wealth saat offline pertama kali) tidak ditampilkan sama sekali, termasuk judulnya (PO-24).

### Q-B8 Maintenance langsung mengganti layar (P1)
Temuan: E2E-21, UI-44 (layar maintenance).

**Sebagai** nasabah, **saya ingin** langsung tahu bila ORVYN sedang diperbarui, **supaya** saya tidak mencoba transaksi yang pasti ditolak.

1. Aplikasi terbuka di beranda, petugas menyalakan maintenance: dalam 60 detik, tanpa menyentuh HP, layar maintenance tampil.
2. Aplikasi sedang di tengah alur Transfer, petugas menyalakan maintenance: saat nasabah mengetuk tombol berikutnya yang memanggil server, layar maintenance tampil, bukan pesan inline.
3. Layar maintenance hanya berisi judul "ORVYN is being updated", satu kalimat dari server, dan tombol "Try again". Tidak ada judul ganda seperti "Back soon".
4. Petugas mematikan maintenance, nasabah mengetuk "Try again": aplikasi kembali ke layar Unlock atau beranda (bila sesi masih berlaku).
5. Server tetap menolak transaksi selama maintenance (perilaku 0.8.1).

### Q-B9 Layar terkunci bisa dicoba lagi setelah dibuka petugas (P1)
Temuan: E2E-28, E2E-23 (pesan terkunci untuk nasabah yang dikunci petugas).

**Sebagai** nasabah yang terkunci, **saya ingin** mencoba lagi setelah petugas membuka kunci saya, **supaya** saya tidak perlu menutup paksa aplikasi atau melupakan HP.

1. Layar terkunci menampilkan tombol "Try again" (copy sesuai `design.md`).
2. Petugas membuka kunci nasabah di admin, nasabah mengetuk "Try again": PIN pad tampil lagi dengan 5 percobaan.
3. Mengetuk "Try again" saat nasabah masih terkunci: layar tetap terkunci dan memberi tahu bahwa akun masih terkunci.
4. Pesan di layar terkunci benar untuk dua sebab: setelah 5 PIN salah, dan setelah petugas mengunci dari admin. Pesannya tidak menyebut "too many wrong tries" untuk nasabah yang dikunci petugas (PO-7).
5. Membuka aplikasi dari latar belakang setelah petugas membuka kunci juga menampilkan PIN pad, tanpa perlu "Try again".

### Q-B10 E-statement: satu PDF gagal tidak menghapus daftar (P1)
Temuan: UI-37.

**Sebagai** nasabah, **saya ingin** daftar bulan tetap tampil bila satu PDF gagal dibuat, **supaya** saya bisa mencoba lagi atau memilih bulan lain.

1. Pilih bulan saat offline: daftar bulan tetap tampil, dan snackbar dengan pesan error dan "Try again" muncul.
2. Setelah online, mengetuk "Try again" di snackbar membuat PDF bulan itu dan membuka menu bagikan.
3. Gagal memuat daftar bulan itu sendiri tetap memakai error dengan "Try again" (Q-B3).

---

## C. Keamanan dan admin

### Q-C1 Ganti PIN memeriksa PIN lama dulu dan menghitung percobaan (P0)
Temuan: E2E-02 (PIN), E2E-35, UI-45 (PIN).

**Sebagai** nasabah, **saya ingin** PIN lama saya diperiksa dulu dan tebakan salah dibatasi, **supaya** orang yang memegang HP saya tidak bisa menebak PIN tanpa batas.

1. Profile → Change PIN → PIN lama salah: pesan muncul langsung setelah digit ke-6 PIN lama, sebelum PIN baru diminta, dengan sisa percobaan, misalnya "That's not your current PIN. 4 tries left." (copy sesuai `design.md`).
2. Percobaan salah dihitung bersama Unlock dan pembayaran: 2 PIN salah di Change PIN lalu keluar dan buka aplikasi, layar Unlock menyebut 3 tries left untuk PIN salah berikutnya (di DB, `failed_attempts` naik setiap kali).
3. PIN lama salah ke-5 (dari mana pun hitungannya): nasabah terkunci, aplikasi menampilkan layar terkunci, dan admin menampilkan nasabah sebagai locked. Hanya petugas yang bisa membukanya.
4. PIN lama benar: penghitung kembali ke 0, lalu PIN baru diminta dua kali dengan aturan Q-D3 (PIN lemah ditolak di langkah pertama, tidak cocok kembali dengan pesan).
5. Setelah berhasil, layar sukses dengan ikon centang hijau: "Your PIN is changed" / "Use your new PIN from now on.". PIN baru dipakai di Unlock berikutnya.
6. Server: `POST /api/me/pin` dari APK 0.8.1 dengan PIN lama salah juga menaikkan `failed_attempts` (test server).

### Q-C2 Ganti password menghitung percobaan, pesan di field yang benar (P0)
Temuan: E2E-02 (password), E2E-25, UI-45 (password).

**Sebagai** nasabah, **saya ingin** tebakan password lama yang salah dibatasi dan pesannya muncul di field yang tepat, **supaya** akun saya aman dan saya tahu field mana yang salah.

1. Profile → Change password → password lama salah: pesan "That's not your current password." dengan sisa percobaan tampil di bawah field **password lama**, bukan di bawah field password baru.
2. Percobaan salah dihitung ke penghitung yang sama dengan Q-C1, dan salah ke-5 mengunci nasabah dengan layar terkunci.
3. Password lama benar: penghitung kembali ke 0. Password baru yang lemah ditolak di bawah field password baru dengan "Use at least 8 characters, with both letters and numbers.".
4. Setelah berhasil, layar sukses dengan ikon centang hijau (copy sesuai `design.md`), dan masuk berikutnya dengan password baru berhasil.
5. Test server: `POST /api/me/password` dengan password lama salah menaikkan `failed_attempts` dan mengunci di percobaan ke-5.

### Q-C3 Reset demo berhasil walau ada passkey (P0)
Temuan: E2E-03.

**Sebagai** petugas Super Admin, **saya ingin** Reset demo selalu berhasil, **supaya** demo bisa dimulai bersih kapan pun.

1. Satu nasabah punya passkey. Super Admin menjalankan Reset demo dengan "RESET" dan password: reset berhasil, tanpa error 500.
2. Setelah reset, tabel `passkeys` kosong, dan semua data nasabah lain juga kosong (S1.38 AC4).
3. Akun petugas dan audit log tetap ada (S1.38 AC5), dan pengecekan buku besar di dashboard OK.
4. Test server: satu baris dibuat di setiap tabel yang punya foreign key ke `customers`, lalu reset dijalankan, dan semua tabel itu kosong. Tabel baru yang ditambah nanti ikut tertangkap test ini.
5. Masuk dengan passkey lama setelah reset gagal dengan pesan yang jelas (sesuai `design.md`), tanpa crash.

### Q-C4 PIN pad tidak bisa direkam layar (P0)
Temuan: E2E-39, UI-14. Komponen: PinPad.

**Sebagai** nasabah, **saya ingin** layar PIN tidak bisa di-screenshot atau direkam, **supaya** PIN saya tidak bocor lewat rekaman layar.

1. Di setiap layar PIN (Unlock, Create PIN, PIN transaksi, Change PIN, lihat detail kartu): `adb shell screencap` menghasilkan gambar hitam atau gagal.
2. Setelah PIN pad hilang (misalnya layar sukses), screenshot kembali bisa diambil.
3. Detail kartu tetap tidak bisa di-screenshot selama tampil (perilaku 0.8.1 tidak mundur).
4. Tidak ada izin Android baru (pemeriksaan izin saat build lolos).

### Q-C5 Aksi destruktif selalu dikonfirmasi (P0)
Temuan: UI-16.

**Sebagai** nasabah, **saya ingin** dimintai konfirmasi sebelum menghapus sesuatu, **supaya** satu salah ketuk tidak menghapus penerima atau menutup rekening.

1. "Remove from saved" di sheet penerima membuka dialog "Remove {Sri Wahyuni} from saved?" / "You can save them again after your next transfer." dengan tombol "Remove". Membatalkan dialog tidak menghapus apa pun.
2. "Remove budget" membuka dialog "Remove your Food and drinks budget?" / "Reminders for this category stop." dengan tombol "Remove".
3. "Close" di FX Account membuka dialog "Close your USD account?" / "It's empty, so nothing moves. You can open it again when you buy USD." dengan tombol "Close account".
4. Di sheet, tombol destruktif berada di bawah tombol utama dengan jarak minimal 24dp.
5. Tombol destruktif di dialog memakai gaya destruktif dari MASTER, terang dan gelap.

### Q-C6 Status Freeze dan sakelar kartu yang pasti (P1)
Temuan: UI-24.

**Sebagai** nasabah yang kehilangan kartu, **saya ingin** kepastian bahwa kartu saya benar-benar dibekukan, **supaya** saya tenang.

1. Ketuk sakelar Freeze: sakelar langsung berpindah dan menampilkan indikator kecil sampai server menjawab.
2. Snackbar "Card frozen" baru muncul setelah server berhasil, dan visual kartu jadi abu-abu.
3. Offline atau server gagal: sakelar kembali ke posisi semula dan pesan error tampil.
4. Hal yang sama berlaku untuk sakelar Online, Overseas, dan Contactless.

### Q-C7 Server menolak penolakan pendaftaran tanpa alasan (P1)
Temuan: E2E-15.

**Sebagai** pemilik proyek, **saya ingin** setiap penolakan punya alasan, **supaya** pendaftar selalu tahu kenapa ditolak (S1.4 AC4).

1. `POST /admin/applications/:id/reject` dengan alasan kosong ditolak server, dan pendaftaran tetap pending.
2. Alasan yang hanya berisi spasi juga ditolak.
3. Halaman admin menampilkan pesan error yang jelas bila server menolak (copy sesuai `design.md`).
4. Penolakan dengan alasan tetap berhasil, dan pendaftar menerima alasannya (push dan inbox).

### Q-C8 Password awal petugas tidak lewat URL (P1)
Temuan: E2E-16.

**Sebagai** petugas Super Admin, **saya ingin** password awal petugas baru tidak tersimpan di riwayat browser atau log, **supaya** password itu tidak bocor.

1. Tambah petugas: header `Location` dan URL halaman hasil tidak memuat password.
2. Password awal tampil sekali di halaman hasil. Memuat ulang halaman itu tidak menampilkannya lagi.
3. Password awal tidak ada di log server dan tidak ada di Audit Entry.
4. Petugas baru bisa masuk dengan password itu dan wajib menggantinya (S0.2 AC1).

---

## D. Daftar dan masuk

### Q-D1 Sign in: field password dan pesan terlihat walau keyboard terbuka (P0)
Temuan: E2E-04.

**Sebagai** nasabah, **saya ingin** field password dan pesan error selalu terlihat di layar Sign in, **supaya** saya bisa masuk tanpa menebak.

1. Sign in, ketik nomor HP dengan keyboard terbuka: field password terlihat penuh dan bisa diketuk tanpa menutup keyboard.
2. Tombol aksi keyboard di field nomor HP adalah "Next" dan memindahkan fokus ke password. Di field password, tombol aksinya menjalankan Sign in.
3. Password salah: "That phone number and password don't match." terlihat di atas keyboard tanpa menutup keyboard (layar menggulir ke pesan).
4. Uji 200% di 360dp tanpa keyboard: field password dan tombol "Sign in" bisa dicapai dengan menggulir.
5. Offline: "You're offline. Check your connection and try again." terlihat dengan cara yang sama.

### Q-D2 About you: kesalahan terlihat, NIK ganda diperiksa sebelum lanjut (P0)
Temuan: E2E-05 (NIK), UI-17.

**Sebagai** calon nasabah, **saya ingin** tahu field mana yang salah dan apakah NIK saya sudah terdaftar sebelum lanjut, **supaya** saya tidak harus mundur lima langkah.

1. Isi NIK 15 digit, gulir ke bawah, ketuk "Continue": layar menggulir ke field NIK, field itu mendapat fokus, dan TalkBack membacakan errornya. Di atas tombol tampil ringkasan dengan ikon peringatan: "1 answer needs a fix. It's marked above." (dua atau lebih: "{2} answers need a fix. They're marked above.").
2. Kelompok pilihan yang belum dijawab menampilkan "Pick one." di bawah judulnya.
3. NIK 16 digit milik Customer atau pendaftaran yang masih pending: "Continue" menampilkan "This NIK is already registered with ORVYN." di bawah field NIK dan tidak lanjut.
4. NIK yang belum terdaftar: "Continue" lanjut ke langkah berikutnya. Offline: pesan offline dan tidak lanjut.
5. Server: endpoint cek NIK hanya menerima token pendaftaran yang sudah lolos OTP, hanya menjawab tersedia atau sudah terdaftar, dan menolak cek ke-11 untuk pendaftaran yang sama (copy pesan sesuai `design.md`). NIK tidak tertulis di log server (test server).
6. Server tetap menolak NIK ganda saat pendaftaran dikirim (misalnya bila NIK yang sama didaftarkan orang lain di antara dua langkah itu).

### Q-D3 Create PIN: PIN lemah ditolak langsung, PIN tidak cocok diberi tahu (P0)
Temuan: E2E-05 (PIN lemah), E2E-06. Komponen: alur pembuatan PIN bersama.

**Sebagai** calon nasabah, **saya ingin** tahu PIN saya terlalu lemah atau tidak cocok saat itu juga, **supaya** saya tidak bingung di langkah terakhir.

1. Create your PIN `123456`: langsung setelah digit ke-6, "Choose 6 digits that aren't all the same or in a row." tampil, titik kosong lagi, dan layar tetap di "Create your PIN". Hal yang sama untuk `111111` dan `654321`.
2. Create `482915`, Enter it again `482916`: kembali ke "Create your PIN" dengan pesan "Those PINs didn't match. Start again." terlihat.
3. Create `482915`, Enter it again `482915`: lanjut seperti biasa dan pendaftaran terkirim.
4. Aturan yang sama berlaku di Create PIN masuk pertama, PIN baru di Change PIN (Q-C1), dan Create PIN setelah Forgot PIN (Q-D6).
5. Pesan terbaca utuh di uji 200% (Q-A1).

### Q-D4 Kode OTP tetap terlihat bila notifikasi mati (P0)
Temuan: UI-18.

**Sebagai** calon nasabah yang mematikan notifikasi, **saya ingin** kode OTP tetap tampil di layar, **supaya** saya bisa melanjutkan pendaftaran.

1. Notifikasi ORVYN mati, minta kode: layar tidak pindah otomatis setelah jeda.
2. Layar "Enter the code" menampilkan kotak info dengan ikon lonceng: "Notifications are off, so here's your code: {kode}".
3. Kalimat penjelas berbunyi "Enter the 6-digit code for {nomor HP pendaftar}." dan tidak menyebut "as a notification".
4. Notifikasi menyala: perilaku sama dengan 0.8.1 (kode lewat notifikasi, kotak info tidak tampil).
5. Tidak bisa di HP (notifikasi tidak boleh dimatikan di HP pemilik): diverifikasi dengan test Compose atau Robolectric untuk kedua keadaan.

### Q-D5 Langkah selfie punya panah kembali (P1)
Temuan: E2E-29.

**Sebagai** calon nasabah, **saya ingin** bisa kembali dari langkah selfie, **supaya** saya bisa mengulang foto KTP.

1. Langkah selfie menampilkan panah kembali di posisi yang sama dengan langkah KTP, dengan label TalkBack "Back".
2. Mengetuk panah kembali, atau Back sistem, membawa ke langkah KTP.
3. Data yang sudah diisi di langkah sebelumnya tidak hilang.

### Q-D6 "Forgot PIN?" di layar Unlock (P1)
Temuan: UI-33.

**Sebagai** nasabah yang lupa PIN, **saya ingin** tombol yang sesuai dengan situasi saya, **supaya** saya tidak memilih "Not you?" yang melupakan HP saya.

1. Layar Unlock menampilkan "Forgot PIN?" di samping atau di atas "Not you?". Keduanya terlihat di uji 200%.
2. "Forgot PIN?" membuka sheet "Sign in with your phone number and password, then choose a new PIN." dengan tombol "Sign in with password".
3. Setelah masuk dengan nomor HP dan password yang benar, aplikasi langsung ke Create PIN (aturan Q-D3). PIN baru dipakai di Unlock berikutnya, dan HP tetap Registered Device.
4. Server menerima PIN baru tanpa PIN lama hanya dalam 10 menit setelah masuk dengan password di sesi itu. Di luar itu ditolak (test server).
5. Password salah tetap dihitung (S1.5 AC3). Nasabah yang terkunci tidak bisa memakai jalur ini dan melihat layar terkunci.

---

## E. Sistem visual dan aksesibilitas

### Q-E1 Chip pilihan yang jelas di dua tema (P0)
Temuan: UI-10, UI-21. Komponen: Chip, Choices, FilterChip.

**Sebagai** nasabah, **saya ingin** langsung tahu pilihan mana yang aktif, **supaya** saya tidak salah mengira transfer dikirim sekarang atau dijadwalkan.

1. Di terang dan gelap: chip tidak dipilih memakai `surface` dengan garis 1dp `outlineStrong` dan teks `text`. Chip dipilih memakai `primary`, teks `onPrimary`, dan ikon centang 16dp di depan label.
2. Dua pilihan yang saling eksklusif memakai segmented button dengan token yang sama: "Send now"/"Schedule", "Buy"/"Sell", "Renew"/"Pay out".
3. TalkBack membaca status pilihan, misalnya "Schedule, selected".
4. Filter riwayat, pilihan di pendaftaran, dan saran Vyna punya tinggi sentuh minimal 48dp (dicek di `uiautomator dump` bounds).
5. MASTER.md memuat aturan chip dan segmented button yang baru.

### Q-E2 Satu jenis huruf dan kontras teks yang lolos AA (P0)
Temuan: UI-11, UI-12.

**Sebagai** nasabah, **saya ingin** semua teks memakai huruf yang sama dan mudah dibaca, **supaya** aplikasi terlihat selesai dan terbaca di bawah sinar matahari.

1. Token `textTertiary` di tema terang bernilai #616D86. Tema gelap tidak berubah.
2. Kelima belas peran tipografi Material 3 didefinisikan dengan Plus Jakarta Sans dan angka tabular. Tidak ada teks Roboto di layar Wealth, Insights, investasi, deposito, valas, Vyna, kantong, kartu RM, dan pemilih tanggal (dicek di screenshot).
3. Nominal di layar beli/jual valas ("JPY 1,000") memakai huruf yang sama dengan nominal Rupiah.
4. Keterangan baris (persen aset, tenor, NAB, subjudul) berukuran 14sp. 12sp hanya untuk caption di bawah grafik.
5. Tabel kontras di MASTER §1 diperbarui dengan nilai #616D86.

### Q-E3 Transisi layar yang memberi arah (P0)
Temuan: UI-13.

**Sebagai** nasabah, **saya ingin** perpindahan layar terasa cepat dan menunjukkan arah maju atau mundur, **supaya** saya tidak kehilangan arah di tengah alur.

1. Maju ke layar baru: layar masuk dengan geser dari kanan dan memudar dalam 250ms. Kembali: arah sebaliknya. Tidak ada fade 700ms.
2. Langkah di dalam alur (penerima, nominal, periksa, PIN) juga berganti dengan arah yang sama.
3. Bila animasi dimatikan di HP (animator scale 0), layar berganti langsung tanpa animasi (test otomatis).
4. Manifest memuat `android:enableOnBackInvokedCallback="true"`, dan Back sistem di setiap alur tetap mundur satu langkah.
5. Durasi dan kurva sesuai MASTER §4 (dicek di kode).

### Q-E4 Ketukan ganda tidak terbawa ke layar berikutnya (P0, PO-1)
Temuan: E2E-36 (= F3).

**Sebagai** nasabah, **saya ingin** ketukan ganda pada tombol hanya dihitung sekali, **supaya** satu jatah PIN saya tidak terpakai dan langkah tidak terlewat.

1. Ketuk "Send Rp50,000" dua kali cepat: layar PIN terbuka dengan 0 digit terisi.
2. Beli Bond, ketuk "Buy Rp1,000,000" dua kali cepat: layar PIN terbuka dengan 0 digit terisi.
3. Buka deposito, ketuk Next dua kali cepat: layar tenor tampil dan tidak terlewat.
4. Hal yang sama untuk "Continue" di nominal Transfer dan "Next" di semua alur dengan tombol bawah (dicek di test otomatis).
5. Mengetik 6 digit PIN secepat mungkin di PIN pad tetap mengisi 6 digit (tidak ada ketukan sah yang tertelan).

### Q-E5 Semua sakelar membacakan statusnya (P0)
Temuan: E2E-07.

**Sebagai** pengguna TalkBack, **saya ingin** tahu apakah sakelar hidup atau mati, **supaya** saya tahu kartu saya sudah dibekukan.

1. `uiautomator dump` di Cards: baris Freeze card, Online, Overseas, dan Contactless masing-masing `checkable=true` dengan `checked` sesuai posisi.
2. TalkBack membaca misalnya "Freeze card, switch, on".
3. Hal yang sama berlaku untuk setiap sakelar lain di aplikasi (biometrik, Read replies aloud, tabung otomatis, dan lainnya).
4. Mengetuk di mana saja pada baris mengubah sakelar sekali (tidak dua kali).

### Q-E6 Vyna bisa dipakai pengguna TalkBack dan jawabannya rapi (P0)
Temuan: UI-19, UI-29.

**Sebagai** nasabah, **saya ingin** jawaban Vyna dibacakan sekali dan tampil rapi, **supaya** saya bisa memakai Vyna dengan TalkBack dan membaca jawaban panjang.

1. Selama jawaban masuk per potongan, gelembung tidak memakai live region. Status "Vyna is thinking…" diumumkan sekali.
2. Setelah jawaban selesai, seluruh jawaban diumumkan sekali (test semantics).
3. Daftar bertanda "-" dan teks tebal tampil sebagai bullet dengan indentasi dan huruf tebal, bukan simbol mentah.
4. Ikon pembaca suara: speaker saat aktif, speaker tercoret saat mati, dengan label TalkBack yang berbeda.
5. Animasi "thinking" diam bila animasi dimatikan.
6. Disclaimer berbunyi "Vyna can get things wrong. Check the details before you send money.".

### Q-E7 Label TalkBack dan tanda belum dibaca di inbox (P1)
Temuan: E2E-32, UI-32, UI-31.

**Sebagai** pengguna TalkBack, **saya ingin** label yang bermakna, **supaya** saya tahu apa yang saya dengar.

1. Saldo tersembunyi (beranda, kartu rekening, kantong) dibaca "Balance hidden", bukan "R P bullet".
2. Ikon bagikan e-statement menyebut bulannya, misalnya "Share September 2026 statement" (sesuai `design.md`).
3. Baris inbox yang hanya informasi tidak diumumkan "disabled". Baris yang punya tautan memakai role Button.
4. Judul inbox belum dibaca tebal 700, sudah dibaca 600 dengan teks `textSecondary`. Titik belum dibaca tetap ada.

### Q-E8 Baris yang terlihat bisa diketuk selalu membuka sesuatu (P1)
Temuan: UI-23, E2E-31, UI-42.

**Sebagai** nasabah, **saya ingin** setiap baris yang bereaksi saat diketuk benar-benar membuka sesuatu, **supaya** aplikasi tidak terasa rusak.

1. Baris pembayaran kartu terakhir di Cards membuka detail transaksinya.
2. Di Wealth, seluruh baris aset bisa diketuk dengan chevron, tulisan "Open" dihapus, dan "Savings and pockets" membuka detail rekening ORVYN Everyday (PO-16).
3. Label TalkBack persen aset sama dengan teks di layar ("Less than 1%", bukan "Gold 0%").
4. Segmen grafik komposisi minimal 6dp, termasuk aset 1%.
5. "Grow your money" hanya menampilkan produk yang belum dimiliki nasabah, dan "Not taken off your assets" diganti "Not counted in your total".

### Q-E9 Status permintaan uang dengan ikon dan kata (P1)
Temuan: UI-22.

**Sebagai** nasabah, **saya ingin** status permintaan uang mudah dibaca, **supaya** saya tahu mana yang sudah dibayar.

1. Daftar dan detail permintaan memakai StatusChip (ikon dan teks): Waiting (`textSecondary` di `surfaceVariant`, ikon jam), To pay (`warning`), Paid (`success`), Declined, Cancelled, Expired (`textSecondary`).
2. Status tidak dibedakan hanya dengan warna: setiap chip punya ikon dan kata.
3. Layar split bill memakai chip yang sama untuk status per orang.
4. Terbaca di terang, gelap, dan uji 200%.

### Q-E10 Ikon tagihan yang berbeda per jenis (P1)
Temuan: UI-25.

**Sebagai** nasabah, **saya ingin** ikon yang berbeda untuk setiap jenis tagihan, **supaya** daftar Bills cepat dipindai.

1. Pulsa memakai ikon HP, BPJS ikon P3K, e-wallet ikon dompet, PLN tetap.
2. Ikon berasal dari Phosphor Regular sebagai vector drawable, ukuran sama dengan ikon lain di daftar.
3. Tampil benar di terang dan gelap.

### Q-E11 Grafik Insights terbaca dan pesan kategori kosong yang tepat (P1)
Temuan: UI-28, UI-44 (kategori kosong).

**Sebagai** nasabah, **saya ingin** batang bulan lalu terlihat jelas dan pesan kosong yang cocok, **supaya** saya bisa membandingkan bulan.

1. Batang bulan lain berwarna #7088C4 di terang dan #55689C di gelap. Batang terpilih tetap `primary`.
2. Ringkasan TalkBack grafik yang ada tetap dibacakan (S2.9 AC4).
3. Kategori tanpa pengeluaran bulan ini menampilkan "No {Food and drinks} spending this month." dan tidak menyebut kartu kredit.

### Q-E12 Skeleton yang tenang dan tanpa strip status bar (P1)
Temuan: UI-39, UI-40.

**Sebagai** nasabah, **saya ingin** loading yang tidak berkedip dan bagian atas layar yang rapi, **supaya** aplikasi terasa halus.

1. Skeleton memakai `surfaceVariant`, berdenyut 1,2 detik, dan baru tampil bila memuat lebih dari 300ms.
2. Satu layar mengumumkan "Loading" sekali, bukan per blok.
3. Saldo hero saat memuat tampil sebagai blok skeleton 200×40dp, bukan "Rp—".
4. Tema terang: saat hero tergulir hilang, tidak ada strip biru tua di bawah status bar, dan ikon status bar berubah gelap.

### Q-E13 Kartu satu gaya dan nama panjang di gambar bukti (P1)
Temuan: UI-41, UI-43.

**Sebagai** nasabah, **saya ingin** kartu yang seragam dan bukti yang tidak terpotong, **supaya** aplikasi terasa satu produk dan bukti saya rapi saat dibagikan.

1. Kartu di Scheduled, Requests, Insights, dan Pockets memakai garis `outlineVariant` dan radius 14, sama dengan OrvynCard. Tile ikon radius 10.
2. Gambar bukti dengan nama penerima 40 huruf: nama dibungkus maksimal 2 baris lalu elipsis, tidak keluar dari gambar.
3. Gambar bukti lain tidak berubah.

---

## F. Story lama yang dibangun sekarang

### Q-F1 Detail rekening (S1.9) (P0)
Temuan: E2E-11, UI-09.

**Sebagai** nasabah, **saya ingin** membuka detail rekening tabungan saya dan membagikan nomornya dengan rapi, **supaya** orang bisa mengirim uang ke saya.

1. Mengetuk kartu ORVYN Everyday di beranda membuka layar "ORVYN Everyday" berisi nomor rekening dalam grup 4 angka, saldo, "Opened on {tanggal}" (misalnya "Opened on 3 Mar 2024"), dan riwayat rekening itu.
2. "Share account details" (tombol utama) membuka menu bagikan Android dengan teks "{nama pemilik} · ORVYN Bank · {nomor dalam grup 4}".
3. "Copy number" (tombol sekunder) menyalin nomor tanpa spasi dan menampilkan "Account number copied".
4. Bila saldo disembunyikan di beranda, saldo di detail juga tersembunyi dan dibaca "Balance hidden" (PO-9).
5. Offline: data terakhir tampil dengan banner offline, dan Share serta Copy tetap bekerja.
6. Layar lolos uji 200% dan punya keadaan loading, error dengan "Try again".

### Q-F2 "Share receipt" dan tanggal di layar sukses (P0)
Temuan: E2E-12, UI-08.

**Sebagai** nasabah, **saya ingin** membagikan bukti langsung dari layar sukses, **supaya** saya bisa mengirimnya ke penjual atau keluarga lewat WhatsApp.

1. Layar sukses Transfer (kirim sekarang), QRIS, Bill Payment, dan bayar Money Request menampilkan "Share receipt" sebagai tombol sekunder di atas "Done".
2. "Share receipt" membuka menu bagikan dengan gambar bukti yang sama dengan bukti dari riwayat (S1.11 AC3), dan gambar tidak tersimpan ke galeri.
3. Baris pertama rincian di semua layar sukses adalah "Date", misalnya "16 Oct 2026, 19:27 WIB", sama dengan waktu di detail transaksi.
4. Di sukses Transfer ke penerima baru, "Save {nama} as a recipient" tetap ada, di atas "Share receipt".
5. Sukses transfer terjadwal tidak menampilkan "Share receipt" (PO-10), dan layar sukses yang memindahkan uang antar rekening sendiri (kantong, valas, deposito, investasi, tagihan kartu) juga tidak.

### Q-F3 Filter riwayat Date dan Type (S1.10 AC3) (P1)
Temuan: E2E-14, UI-34.

**Sebagai** nasabah, **saya ingin** memfilter riwayat per tanggal dan jenis, **supaya** saya bisa menemukan transaksi bulan lalu atau semua pembayaran QRIS.

1. Riwayat punya filter "Date" yang membuka sheet: This month, Last month, Last 3 months, Pick dates. "Pick dates" memakai pemilih rentang tanggal, dan tanggal akhir tidak bisa sebelum tanggal mulai.
2. Filter "Type": Transfers, QRIS, Bills, Cards, Pockets, Wealth (PO-11). Hasil hanya berisi jenis yang dipilih.
3. Date, Type, Money in/Money out, dan pencarian bisa digabung. Chip filter menampilkan pilihan aktif, dan filter bisa dihapus.
4. Tarik ke bawah memperbarui riwayat.
5. Bila halaman berikutnya gagal dimuat (misalnya offline saat menggulir), baris terakhir menampilkan "Couldn't load more. Try again". Mengetuknya setelah online memuat halaman itu.
6. Filter yang tidak punya hasil menampilkan keadaan kosong (sesuai `design.md`).

### Q-F4 Nominal opsional di QR-ku (S1.16 AC1) (P1)
Temuan: E2E-17.

**Sebagai** nasabah, **saya ingin** menambahkan nominal ke QR-ku, **supaya** teman tinggal scan dan membayar jumlah yang tepat.

1. Layar QR-ku punya aksi untuk menambah nominal (copy sesuai `design.md`). Setelah nominal diisi, QR berubah dan nominal tampil di bawah QR.
2. Nominal di bawah Rp10,000 tidak bisa dipakai, dengan label tombol "Minimum Rp10,000" (PO-25).
3. Nominal bisa dihapus, dan QR kembali tanpa nominal.
4. Isi QR dengan nominal memuat nomor rekening dan nominal itu (test otomatis: QR di-decode). Scan oleh nasabah ORVYN lain membuka Internal Transfer dengan penerima dan nominal terisi (tidak bisa di HP, diverifikasi dengan test).
5. Kecerahan layar tetap naik selama QR tampil dan kembali setelahnya (S1.16 AC3).

### Q-F5 Pulsa: nomor sendiri terisi, operator tampil saat mengetik (S1.20) (P1)
Temuan: E2E-18.

**Sebagai** nasabah, **saya ingin** nomor saya sendiri sudah tersedia dan operator langsung terlihat, **supaya** membeli pulsa lebih cepat.

1. Layar pulsa menampilkan nomor HP nasabah (dari data Customer di server) sebagai pilihan pertama. Mengetuknya mengisi nomor dan menampilkan operatornya.
2. Mengetik nomor lain: nama operator tampil setelah 4 digit pertama, sebelum "Continue" (PO-23).
3. Awalan yang tidak dikenal menampilkan pesan yang jelas setelah 4 digit, dan "Continue" nonaktif.
4. Aplikasi tidak membaca nomor dari SIM atau HP (tidak ada izin baru, pemeriksaan izin saat build lolos).

### Q-F6 Emas dalam gram dan unit di detail investasi (S3.6 AC5, S3.8 AC2) (P1)
Temuan: E2E-19.

**Sebagai** nasabah, **saya ingin** membeli emas dalam gram dan melihat berapa unit atau gram yang saya punya, **supaya** saya tahu persis kepemilikan saya.

1. Layar beli Gold Savings punya pilihan "Rupiah" dan "Grams" (segmented button, Q-E1). Mode Rupiah sama dengan 0.8.1, termasuk Quick Amount.
2. Mode gram: nasabah mengetik gram dengan 2 desimal, tanpa Quick Amount (PO-12). Rupiah yang akan dibayar tampil sebelum PIN, sama dengan gram × harga per gram dibulatkan ke atas.
3. Di bawah 0,01 g atau di bawah Rp50,000, tombol nonaktif dengan label yang menyebut minimumnya (sesuai `design.md`).
4. Beli 0,05 g dengan PIN: nominal Journal Entry sama persis dengan Rupiah yang tampil sebelum PIN, gram di Holding bertambah 0,05, dan buku besar Balanced.
5. Detail Fund menampilkan unit yang dimiliki, dan detail Gold Savings menampilkan gram, di samping nilai hari ini (S3.6 AC5, S3.8 AC3).

### Q-F7 Bayar permintaan uang lewat layar periksa (S2.6, K5) (P1)
Temuan: E2E-20.

**Sebagai** nasabah yang dimintai uang, **saya ingin** melihat detail permintaan dan memeriksa pembayaran sebelum PIN, **supaya** saya tahu persis apa yang saya bayar.

1. Mengetuk kartu permintaan di inbox membuka detail permintaan: peminta, nominal, catatan, tanggal, status, dan tombol "Pay" dan "Decline".
2. "Pay" (dari detail atau dari layar Requests) membuka layar periksa: penerima, nominal, biaya Rp0, saldo setelah bayar. Lalu PIN, lalu sukses dengan "Share receipt".
3. "Decline" membuka dialog konfirmasi destruktif (copy sesuai `design.md`, PO-26). Setelah dikonfirmasi, peminta menerima push (S2.6 AC5).
4. Permintaan yang sudah dibayar, ditolak, dibatalkan, atau kedaluwarsa menampilkan statusnya tanpa tombol Pay dan Decline.
5. Journal Entry pembayaran sama dengan Internal Transfer 0.8.1, terkirim sekali walau tombol diketuk dua kali, dan buku besar Balanced.

### Q-F8 Admin: filter jenis lengkap, banner dengan tanggal mulai dan urutan, Settings (S1.35, S1.36, S2.19) (P1)
Temuan: E2E-34, E2E-22.

**Sebagai** petugas, **saya ingin** memfilter semua jenis transaksi dan mengatur banner sepenuhnya, **supaya** saya bisa mengelola demo tanpa bantuan teknis.

1. Filter jenis di halaman Transactions memuat setiap jenis transaksi yang ada di buku besar, termasuk kantong, valas, deposito, dan investasi. Memilih satu jenis hanya menampilkan jenis itu.
2. Formulir banner punya tanggal mulai, tanggal berakhir, dan urutan. Banner yang tanggal mulainya besok tidak tampil di aplikasi hari ini, dan tampil setelah tanggal itu.
3. Banner yang sudah ada bisa diedit (gambar, judul, teks, detail, jadwal, urutan). Setiap edit tercatat sebagai Audit Entry dengan nilai sebelum dan sesudah.
4. Aplikasi menampilkan banner aktif sesuai urutan dari admin.
5. Setelah migrasi, banner lama punya tanggal mulai sama dengan tanggal dibuat dan urutan sama dengan urutan sebelumnya (PO-21).
6. Settings tidak punya sakelar Widget, dan itu sesuai S2.19 AC3 yang baru (K4). Sakelar fitur lain tetap bekerja.

---

## G. Copy dan detail

### Q-G1 Banner promo di bawah transaksi terakhir (K1) (P0)
Temuan: UI-15, E2E-33.

**Sebagai** nasabah, **saya ingin** melihat uang saya dulu sebelum promo, dan banner yang tidak berganti di bawah jari saya, **supaya** beranda berguna dan bisa diandalkan.

1. Urutan beranda: hero, aksi cepat, kartu insight, rekening, kantong, transaksi terakhir, lalu banner. Saat offline, banner offline tepat di bawah hero (Q-B6).
2. Dengan TalkBack aktif (touch exploration), carousel tidak berputar otomatis. Setelah TalkBack mati, putar otomatis kembali.
3. Mengetuk pil aksi di banner (misalnya "Scan to pay") langsung menjalankan aksinya tanpa membuka detail. Mengetuk bagian lain banner membuka detail banner itu.
4. Mengetuk banner selalu membuka banner yang sedang terlihat saat diketuk, bukan banner berikutnya (diuji 10 kali, termasuk tepat saat putar otomatis berganti).
5. Bila tidak ada banner aktif, bagian ini tidak tampil dan tidak menyisakan ruang (S1.8 AC4).

### Q-G2 Pemindai QRIS dengan senter dan pesan yang terbaca (P0)
Temuan: UI-20.

**Sebagai** nasabah yang membayar di warung yang remang, **saya ingin** menyalakan senter dari pemindai, **supaya** QR tetap terbaca.

1. Pemindai punya tombol senter 48dp di kanan atas. Ikon Regular saat mati dan Fill saat menyala. TalkBack membaca "Turn on the flashlight" / "Turn off the flashlight".
2. Senter memakai `CameraControl.enableTorch`. Tidak ada izin baru, dan senter mati saat pemindai ditutup.
3. HP tanpa senter tidak menampilkan tombolnya.
4. Pesan error tampil sebagai chip putih dengan ikon peringatan dan teks `text`, dan dibacakan TalkBack (live region polite).
5. Deskripsi kamera untuk TalkBack: "Camera. Point it at a QRIS code.", tanpa "(attempt 2)".
6. Tidak bisa di HP (aturan kamera): diverifikasi dengan test Compose dan review kode.

### Q-G3 Bantuan dengan "Ask Vyna" dan halaman Privacy yang lengkap (S1.31, K3) (P1)
Temuan: UI-30, E2E-24.

**Sebagai** nasabah, **saya ingin** tahu jawaban FAQ bisa dibuka, punya jalan bila jawabannya tidak ada, dan membaca penjelasan privasi yang lengkap, **supaya** saya merasa aman.

1. Setiap pertanyaan FAQ punya chevron yang berputar 180° saat terbuka, dan TalkBack membaca "expanded" atau "collapsed".
2. Di bawah daftar FAQ ada baris "Still need help?" dengan aksi "Ask Vyna" yang membuka Vyna.
3. Bila Vyna dimatikan di admin, baris "Still need help?" tidak tampil (PO-17).
4. Tidak ada nomor telepon dan tidak ada tombol yang membuka dialer di layar Bantuan.
5. Halaman Privacy menyebut kamera untuk pemindai QRIS **dan** foto KTP serta selfie saat pendaftaran (foto tidak dikirim dan langsung dihapus), dan mikrofon hanya saat tombol mikrofon Vyna ditekan, diproses di HP. Semua baris PRD 7.4 tercakup (copy sesuai `design.md`).

### Q-G4 Format angka, tanggal, dan kurs yang konsisten (P1)
Temuan: E2E-23 (format).

**Sebagai** nasabah, **saya ingin** angka dan tanggal ditulis dengan cara yang sama di mana-mana, **supaya** saya tidak salah membaca.

1. Periode tagihan PLN dan BPJS tampil "Bill for Oct 2026", bukan "2026-10".
2. Persen dan nominal negatif memakai tanda minus asli U+2212 ("−1.30%"), bukan tanda hubung.
3. "Rp" tidak pernah tampil sebagai "RP", termasuk di judul kecil berhuruf kapital seperti "YOUR INVESTMENTS" (bentuk persisnya sesuai `design.md`).
4. Kurs JPY ditampilkan per 100 unit dengan 2 desimal, misalnya "100 JPY = Rp10,802.50" (PO-13). Hitungan dan kurs tersimpan tidak berubah.

### Q-G5 Copy yang tepat untuk situasinya (P1)
Temuan: E2E-23 (copy).

**Sebagai** nasabah, **saya ingin** setiap teks cocok dengan yang sedang terjadi, **supaya** saya tidak bingung.

1. Baris "Arrives" di layar periksa RTGS hanya menyebut waktu tiba (sesuai `design.md`), tanpa aturan "for amounts above Rp100,000,000".
2. Tombol di layar tukar valas menyebut aksi dan nominal (sesuai `design.md`, PO-14), bukan "Review", dan tetap membuka PIN.
3. Subjudul Sign in tidak menyebut "the ORVYN team sent you" (sesuai `design.md`), benar untuk nasabah dari persona dan yang mendaftar sendiri.
4. Dialog batal permintaan uang punya tombol "Keep request" dan "Cancel request" (PO-15).
5. Layar Unlock selalu menampilkan "Welcome back, {nama depan}", baik setelah keluar otomatis maupun setelah aplikasi dibuka ulang.

### Q-G6 Pesan untuk pencarian kosong dan nominal di bawah minimum (P1)
Temuan: E2E-26, E2E-27.

**Sebagai** nasabah, **saya ingin** tahu kenapa tidak ada hasil atau kenapa tombol tidak aktif, **supaya** saya tahu harus berbuat apa.

1. Pencarian penerima "zzz" tanpa hasil menampilkan pesan kosong yang menyebut kata yang dicari (sesuai `design.md`), bukan layar kosong.
2. Top up e-wallet Rp5,000: tombol nonaktif dengan label "Minimum Rp10,000".
3. Pada Rp10,000 ke atas, tombol aktif dengan label biasa.

### Q-G7 Back dari layar periksa yang disiapkan Vyna kembali ke Vyna (P1)
Temuan: E2E-30.

**Sebagai** nasabah, **saya ingin** satu tekan Back membawa saya kembali ke Vyna, **supaya** saya bisa mengubah permintaan saya.

1. Vyna menyiapkan transfer, nasabah mengetuk "Review", lalu Back sekali: kembali ke percakapan Vyna.
2. Tidak ada uang yang bergerak, dan percakapan tetap utuh.
3. Hal yang sama untuk pembayaran tagihan dan pengisian kantong yang disiapkan Vyna.

### Q-G8 Kurs dari sudut pandang nasabah dan grafik Fund yang menjawab "naik berapa" (P1)
Temuan: UI-26, UI-27.

**Sebagai** nasabah, **saya ingin** kurs dan grafik yang langsung saya pahami, **supaya** saya tidak salah membaca harga.

1. Tabel kurs memakai kolom "You sell at" (nilai Buy Rate) dan "You buy at" (nilai Sell Rate).
2. Chip rentang grafik Fund berbunyi "1 month", "6 months", "1 year".
3. Di atas grafik tampil perubahan untuk rentang yang dipilih, misalnya "+4.10% in 6 months" (hijau dengan panah naik), atau dengan panah turun berwarna teks biasa, tidak merah.
4. Nilai tertinggi dan terendah tampil sebagai label tipis di tepi kanan grafik.

### Q-G9 Tidak ada field teks di layar keypad nominal (P1)
Temuan: UI-35.

**Sebagai** nasabah, **saya ingin** mengetik catatan tanpa dua keyboard bersaing, **supaya** layar tidak sempit.

1. Layar nominal minta uang tidak punya field catatan. Catatan diisi di langkah berikutnya, seperti Transfer (PO-19).
2. Tidak ada layar lain yang punya field teks dan keypad nominal sekaligus (termasuk split bill).
3. Catatan tetap terkirim ke penerima dan tampil di detail permintaan.

### Q-G10 Judul riwayat kantong yang jelas arahnya (P1)
Temuan: UI-36.

**Sebagai** nasabah, **saya ingin** riwayat kantong menyebut arah uangnya, **supaya** saya tidak mengira uang datang dari orang lain.

1. Uang masuk ke kantong tampil "Added from ORVYN Everyday", uang keluar tampil "Moved to ORVYN Everyday".
2. Transaksi kantong yang dibuat sebelum 0.9.0 juga tampil dengan judul baru (PO-20).
3. Journal Entry lama tidak berubah (dicek di admin), dan buku besar Balanced.

### Q-G11 Copy widget yang jelas (S2.18) (P1)
Temuan: UI-38.

**Sebagai** nasabah, **saya ingin** tombol widget dan keterangan waktu yang jelas, **supaya** saya tahu seberapa baru saldo yang tampil.

1. Tombol widget berbunyi "Show balance".
2. Saat saldo tampil: "As of {14:05} · hides in 10 seconds", dengan jam saat saldo terakhir tersimpan.
3. Saat tersembunyi: "Balance hidden".
4. Tidak bisa di HP (butuh launcher): diverifikasi dengan WidgetTest.
