# User Stories ORVYN: Hotfix 0.9.1, sisa temuan QA quality pass

Sumber: `prd.md` dan `requirement.md` di folder ini, temuan Q-02-F1..F5 di `docs/features/2026-10-16-quality-pass/qa-report.md`, istilah dari `CONTEXT.md`. Desain dan copy: `design.md` di folder ini (ditulis CX, untuk H91-1 dan H91-2). Bila tertulis "sesuai `design.md`", copy-nya ditulis CX.

**Peran:** Nasabah (Customer), Petugas (Officer). Sama dengan Fase 1–3.

**Istilah uji:** sama dengan `docs/features/2026-10-16-quality-pass/stories.md` (Uji 200%, Offline, TalkBack lewat `uiautomator dump` bila TalkBack asli tidak bisa dinyalakan). **Server test** berarti AC diverifikasi dengan test otomatis di `server/test`, karena keadaannya tidak bisa dibuat aman di HP.

**Definisi selesai:**
1. Semua test server dan aplikasi yang ada tetap lolos, ditambah test server baru untuk: minimum Money Request dan Split Bill, `deposit_close` dengan bunga Rp0, dan job jatuh tempo yang tidak tertahan satu deposito gagal.
2. Aplikasi ter-build sebagai **0.9.1 (versionCode 13)**. Daftar izin APK sama persis dengan 0.9.0.
3. Copy di aplikasi dan server sama persis dengan `design.md`.
4. Buku besar dev Balanced di awal, setelah setiap transaksi uji, dan di akhir QA. Tidak ada `kind` Journal Entry baru.
5. Server 0.9.1 tetap melayani APK 0.9.0 dan 0.8.1. Smoke dengan APK 0.9.0: masuk, transfer, minta uang Rp10,000, Insights dan detail kategori.

**Urutan pengerjaan:** H91-3 (server saja, tidak menunggu desain), lalu H91-1, H91-2, H91-4, H91-5.

---

### H91-1 Setiap permintaan uang bisa dibayar
Temuan: Q-02-F2.

**Sebagai** nasabah yang meminta uang atau membagi tagihan, **saya ingin** aplikasi hanya membiarkan saya membuat permintaan yang bisa dibayar, **supaya** teman saya tidak terjebak permintaan yang selalu ditolak.

1. Di layar nominal Ask for money dan layar total Split a bill: Rp0 → tombol nonaktif `Enter an amount`, Rp1 sampai Rp9,999 → tombol nonaktif `Minimum Rp10,000` tanpa error inline, Rp10,000 → `Continue` (minta uang) atau `Next` (split) aktif. Quick Amount 50k, 100k, 250k, 500k tetap tampil.
2. Sheet ubah bagian teman selalu menampilkan `The smallest share is Rp10,000.` di bawah judul. Bagian Rp5,000 → tombol nonaktif `Minimum Rp10,000`. Rp10,000 → `Save` aktif.
3. Split total Rp15,000 dengan satu teman (bagian rata Rp7,500): baris teman menampilkan ikon peringatan dan `Needs at least Rp10,000`, dan tombol nonaktif `Minimum Rp10,000 a share`. Setelah bagian diubah ke Rp10,000, tanda hilang, `Send 1 request` aktif, dan `Your share` menjadi Rp5,000. Dengan TalkBack, baris yang ditandai dibaca dengan tambahan `, Needs at least Rp10,000`.
4. Split total Rp15,000 dengan dua teman menampilkan `This bill is too small for 2 friends at Rp10,000 each. Go back and pick fewer friends.` dan tombol kirim nonaktif. Panah kembali membuka `Who's in?` dengan kedua teman tetap tercentang.
5. Server menolak dengan `BAD_AMOUNT` tanpa membuat permintaan dan tanpa inbox ke teman: permintaan Rp9,999 → `Ask for at least Rp10,000.`, total split Rp9,999 → `The smallest bill you can split is Rp10,000.`, bagian split Rp9,999 → `The smallest share is Rp10,000.` (server test). Di HP dengan APK 0.9.0, minta uang Rp5,000 menampilkan `Ask for at least Rp10,000.` di kotak error. Nilai Rp10,000 diterima. Di detail transaksi, `Split this bill` tidak tampil untuk transaksi di bawah Rp10,000 dan tampil untuk Rp10,000 ke atas.
6. Permintaan Rp10,000 dari Raka yang dibayar Sinta lewat layar periksa dan PIN menghasilkan tepat satu Internal Transfer Rp10,000, statusnya menjadi dibayar, dan buku besar Balanced. Permintaan Rp10,000 yang dibuat sebelum minimum di server dinaikkan ke Rp15,000 tetap bisa dibayar Rp10,000 (server test, H-9).

### H91-2 Detail kategori Insights cocok dengan totalnya
Temuan: Q-02-F3.

**Sebagai** nasabah yang punya kartu kredit, **saya ingin** detail kategori Insights menampilkan semua belanja yang dihitung di totalnya, **supaya** saya tahu persis dari mana angka itu datang.

1. Raka → History → Insights → `Shopping`: detail menampilkan transaksi bulan ini di kategori itu dari ORVYN Everyday dan dari kartu kreditnya. Jumlah nominal semua baris sama dengan nominal `Shopping` di Insights.
2. Subjudul layar `This month, from ORVYN Everyday and your cards.` Baris kartu memakai ikon kartu dan subjudul yang diawali nama kartu dan 4 digit terakhir (misalnya `ORVYN Voyage ·••• 4821 · Thu, 15 Oct, 20:12`). Baris tabungan sama dengan 0.9.0. Dengan TalkBack, baris kartu dibaca `{merchant}, {nama kartu} card ending {4 digit}, {hari}, {jam}, {nominal}` tanpa membacakan titik. Terang dan gelap.
3. Baris diurutkan dari yang terbaru, campur kedua sumber. Mengetuk baris tabungan membuka detail transaksi. Mengetuk baris kartu membuka detail yang sama dengan bila diketuk dari layar Cards.
4. Kategori yang bulan ini hanya punya belanja kartu kredit menampilkan baris kartu itu, bukan keadaan kosong. Keadaan kosong "No {kategori} spending this month." hanya tampil bila tidak ada transaksi dari kedua sumber. (Data dibuat petugas dengan simulasi transaksi kartu, S1.26. Bila kategorinya tidak bisa diatur dari admin, diverifikasi dengan test.)
5. Offline lalu membuka detail kategori: error state dengan `You're offline. Check your connection and try again.` dan `Try again`, tanpa daftar dari cache. Online lalu `Try again` memuat daftar lengkap. Nasabah tanpa kartu kredit (nasabah baru dari pendaftaran) melihat transaksi tabungan saja, dengan jumlah yang sama dengan nominal kategorinya.
6. Uji 200% (360dp dan 411dp, terang dan gelap): nominal, nama, dan nama kartu di setiap baris terbaca utuh, tidak terpotong.

### H91-3 Deposito dengan bunga Rp0 jatuh tempo tanpa error
Temuan: Q-02-F4.

**Sebagai** petugas, **saya ingin** "Mature now" dan jatuh tempo otomatis selalu berhasil walau bunganya Rp0, **supaya** pokok nasabah kembali ke tabungan dan deposito lain tidak ikut tertahan.

1. Hendra membuka deposito Rp1,000,000 hari ini, mengubah `At maturity` ke `Pay out`, lalu petugas menjalankan `Mature now`: admin menampilkan "Matured. Interest after tax: Rp0." (bukan HTTP 500), ORVYN Everyday Hendra bertambah Rp1,000,000, deposito hilang dari Wealth, dan inbox berisi "Deposit paid out".
2. Pencairan di AC1 menghasilkan tepat satu Journal Entry `deposit_close` dengan dua Posting: Time Deposit −1,000,000 dan Savings Account +1,000,000. Tidak ada Posting bernilai Rp0, dan buku besar tetap Balanced.
3. Deposito dengan bunga lebih dari Rp0 tetap menghasilkan satu `deposit_close` dengan empat Posting seperti 0.9.0 (QA putaran 2: Time Deposit −1,000,000, beban bunga −2,876, pajak +575, Savings Account +1,002,301). Bila pajaknya dibulatkan ke Rp0, Posting pajak tidak ditulis (server test).
4. Deposito dengan `Renew` dan bunga Rp0 tetap diperpanjang tanpa Journal Entry, dan inbox berisi "Deposit renewed", seperti 0.9.0.
5. Job jatuh tempo dengan dua deposito yang jatuh tempo di menit yang sama, dan yang pertama gagal: deposito kedua tetap dicairkan atau diperpanjang. Deposito pertama tetap terbuka tanpa Journal Entry, kegagalannya tertulis di log server, dan dicoba lagi di putaran berikutnya (server test).
6. Dengan suku bunga deposito 0% di Settings admin, deposito `Pay out` yang jatuh tempo lewat job mengikuti AC2: satu `deposit_close` dengan dua Posting (server test).

### H91-4 Done di keyboard menjalankan tombol utama
Temuan: Q-02-F1.

**Sebagai** nasabah, **saya ingin** tombol Done di keyboard melanjutkan langkah saya, **supaya** saya tidak perlu menutup keyboard lalu mencari tombolnya.

1. Transfer → `New recipient` → pilih bank → ketik nomor rekening yang panjangnya valid → Done (atau Enter di keyboard fisik, `keyevent 66`) menjalankan "Check account", sama dengan mengetuk tombolnya. Dengan nomor yang terlalu pendek, Done hanya menutup keyboard dan layar tetap.
2. Pay → `PLN token` → `Meter number` 12 digit → Done menjalankan "Continue". Hal yang sama berlaku di tagihan PLN, BPJS, pulsa, dan e-wallet. Dengan nomor yang belum lengkap, Done hanya menutup keyboard.
3. Done menjalankan tombol utama bila aktif di: `ORVYN account number` di Ask for money dan Split (`Find`, bila 10 digit), `Nickname` di sheet simpan penerima (`Save`), dan `New password` di Change password (`Save password`). Di Change password, Next di `Current password` memindahkan fokus ke `New password`.
4. Done di field catatan layar periksa menjalankan tombol utama layar itu bila aktif: review Transfer membuka PIN pad (tanpa uang bergerak sebelum PIN benar), "Check the request" mengirim permintaan, dan "Check the shares" mengirim permintaan split. Bila tombolnya nonaktif, Done hanya menutup keyboard.
5. Done atau Search di field pencarian (Find a bank, cari penerima di Send money, cari riwayat, cari FAQ) hanya menutup keyboard. Hasil pencarian tetap tampil dan tidak ada layar lain yang terbuka.
6. Regresi: aksi keyboard di pendaftaran, Sign in, dan kantong tetap seperti 0.9.0 (Q-01-F11), jenis keyboard setiap field tidak berubah, dan Enter di kotak tanya Vyna tetap membuat baris baru (tidak mengirim).

### H91-5 Snackbar beranda tidak menutupi tombol QRIS
Temuan: Q-02-F5.

**Sebagai** nasabah, **saya ingin** pesan singkat di beranda tidak menutupi tombol apa pun, **supaya** saya tetap bisa melihat dan mengetuk QRIS.

1. Beranda offline → ketuk aksi `Transfer`: snackbar offline tampil dengan tepi bawahnya 12dp di atas tepi atas tombol QRIS yang menonjol. Tidak ada bagian tombol QRIS, bayangannya, labelnya, atau item bottom nav lain yang tertutup (screenshot, terang dan gelap).
2. Selama snackbar tampil, tombol QRIS dan item bottom nav lain tetap bisa diketuk dan bereaksi seperti biasa.
3. Uji 200% beranda dengan snackbar tampil (360dp dan 411dp, terang dan gelap): snackbar tidak menutupi tombol QRIS, dan teks snackbar terbaca utuh.
4. Tab lain yang punya bottom nav memakai posisi snackbar yang sama (dicek di kode atau test Compose bila snackbar tidak bisa dipicu di HP).
5. Regresi: snackbar di layar tanpa bottom nav tetap seperti 0.9.0, misalnya di layar sukses Transfer offline → `Save as a recipient`, snackbar di atas tombol itu dan tidak menutupi `Share receipt` atau `Done` (Q-01-F2).
