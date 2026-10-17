# Desain: Hotfix 0.9.1

Penulis: CX, 17 Okt 2026. Sumber: `requirement.md` (keputusan final orkestrator), temuan Q-02-F1..F5 di `docs/features/2026-10-16-quality-pass/qa-report.md` (Putaran 2, screenshot `qa-shots-0.9.0/r2-insights-shopping-detail.png` dan `r2-f2-snackbar-home-offline.png`), desain 0.9.0 `docs/features/2026-10-16-quality-pass/design.md` (4.7, 4.11, 4.12, 9.11, 17.2, 17.3), `design-system/MASTER.md`, dan kode `android/app/src/main/java/id/orvyn/app/ui/` (`requests/Requests.kt`, `insights/Insights.kt`, `components/Patterns.kt`, `App.kt`, `home/HomeScreen.kt`, `history/HistoryScreen.kt`) serta `server/src/requests.ts`, `insights.ts`, `cards.ts`.

**Cara membaca.** Sama dengan desain 0.9.0: copy dalam `kode` adalah teks final bahasa Inggris, IT menyalinnya persis termasuk tanda baca. `{kurung kurawal}` adalah nilai yang diisi. `{minimum}` selalu berarti minimum transfer dari setting server (sekarang `Rp10,000`), diformat dengan `Money.format`. Semua copy baru sudah melewati `humanizer`. Q-02-F4 hanya server, tidak punya tampilan, jadi tidak dibahas di sini.

**Skill.** `ui-ux-pro-max` (label tombol nonaktif yang menjelaskan alasannya, arti tanpa warna, label TalkBack, jarak elemen tetap terhadap bottom nav), `humanizer` (semua copy baru). Tidak ada grafik baru dan tidak ada palet baru, jadi `dataviz` tidak dipakai.

---

## 1. Ringkasan keputusan CX

1. **Q-02-F2:** di mana pun nominal permintaan atau bagian split diketik, tombol nonaktif mengatakan minimumnya dengan pola 4.7 (`Minimum {minimum}`). Bagian split yang terlalu kecil ditandai di barisnya dengan ikon dan teks, dan tombol kirim mengatakan alasannya. Teks server memakai nilai minimum yang sama, sehingga APK lama pun mendapat pesan yang jelas.
2. **Q-02-F3:** detail kategori Insights memuat transaksi tabungan **dan** kartu kredit, dengan aturan yang sama persis dengan total di baris kategori. Baris kartu menyebut nama kartu dan 4 digit terakhir. Subjudul menyebut kedua sumber, sama dengan subjudul Insights.
3. **Q-02-F1:** tidak ada aturan baru. Aturan Text field MASTER (17.3) diterapkan ke semua field; bagian 4 memetakan setiap field supaya IT dan QA punya daftar yang sama.
4. **Q-02-F5:** di layar dengan bottom nav, snackbar berpatokan pada tepi atas tombol QRIS yang menonjol, bukan tepi atas bar.

---

## 2. Q-02-F2 Minimum permintaan uang dan bagian split

**Aturan (requirement):** setiap permintaan uang dan setiap bagian split minimal `{minimum}`, nilai yang sama dengan minimum transfer. Total split juga minimal `{minimum}`.

**Sumber nilai di aplikasi.** Aplikasi membaca `{minimum}` dari server (cara pastinya diserahkan ke IT, misalnya ikut `/config`). Bila belum pernah termuat, aplikasi memakai nilai terakhir yang diketahui, lalu `Rp10,000`. Server tetap penentu akhir (2.5).

### 2.1 Alur (tidak berubah)

- **Minta uang:** Requests atau beranda `Request` → `Ask for money` (pilih orang) → nominal (`Ask {Sinta}`) → `Check the request` → sukses. Back mundur satu langkah.
- **Split dari Requests:** total (`Split a bill`) → `Who's in?` → `Check the shares` (sheet ubah bagian) → sukses.
- **Split dari detail transaksi:** `Split this bill` → `Who's in?` → `Check the shares`. Back dari `Who's in?` kembali ke detail transaksi.

### 2.2 Layar nominal minta uang dan total split (pola 4.7)

| Nominal | Tombol `Ask {Sinta}` (minta uang) | Tombol `Split a bill` (total) |
|---|---|---|
| `Rp0` | nonaktif `Enter an amount` | nonaktif `Enter an amount` |
| `Rp1` sampai di bawah `{minimum}` | nonaktif `Minimum {minimum}` | nonaktif `Minimum {minimum}` |
| `{minimum}` ke atas | aktif `Continue` (tetap) | aktif `Next` (tetap) |

- Sebelumnya: `Ask for at least Rp1,000` dan total split aktif mulai Rp2,000. Keduanya diganti tabel di atas.
- Seperti Transfer (4.7): **tidak ada** error inline untuk nominal di bawah minimum. Label tombol sudah menjelaskan, dan baris di bawah nominal tetap kosong.
- Quick Amount hanya menampilkan chip yang `>= {minimum}` (MASTER Amount input). Dengan minimum Rp10,000 semua chip bawaan (50k, 100k, 250k, 500k) tetap tampil.
- **TalkBack:** tombol dibaca `Minimum Rp10,000, disabled` (label sama dengan teks yang terlihat). Tidak ada pengumuman tambahan saat label berubah.
- **Terang/gelap:** token tombol nonaktif MASTER (label 38%, isi 12%), tidak ada warna baru.
- **Font 200%:** label tombol boleh 2 baris (4.7, Q-A2 AC5). `Minimum Rp10,000` muat satu baris di 360dp.
- **Memuat, error, offline:** layar ini tidak memuat data (tanpa saldo), jadi tidak ada state baru. Offline baru terasa saat mengirim (2.5).

### 2.3 `Check the shares`

```
┌───────────────────────────────────────────┐
│ ←                                          │
│ Check the shares                           │
│ Total Rp15,000. Tap a share to change it.  │
│ ┌───────────────────────────────────────┐ │
│ │ (SM) Sinta Maharani          Rp5,000  │ │
│ │      ⚠ Needs at least Rp10,000        │ │  ← baru, hanya bila < {minimum}
│ └───────────────────────────────────────┘ │
│ ┌───────────────────────────────────────┐ │
│ │ (BS) Budi Santoso            Rp5,000  │ │
│ │      ⚠ Needs at least Rp10,000        │ │
│ └───────────────────────────────────────┘ │
│ Your share                      Rp5,000   │
│ What for (optional) [               ]     │
│ ⚠ This bill is too small for 2 friends    │  ← baru, hanya bila total < n × {minimum}
│   at Rp10,000 each. Go back and pick      │
│   fewer friends.                          │
├───────────────────────────────────────────┤
│ [ Minimum Rp10,000 a share ] (nonaktif)    │  menempel
└───────────────────────────────────────────┘
```

**Baris teman.** Isi tetap (avatar, nama, nominal). Bila bagiannya di bawah `{minimum}`, di bawah nama muncul baris kedua: ikon `ph_warning_circle` 16dp dan teks `Needs at least {minimum}` (`bodyMedium`, warna `error`). Ikon dan kata membawa arti, bukan hanya warna. Baris kedua hilang begitu bagiannya disimpan `>= {minimum}`.

**Kalimat terlalu kecil.** Muncul bila total split lebih kecil dari jumlah teman × `{minimum}`, karena mengubah bagian saja tidak akan cukup: `This bill is too small for {n} friends at {minimum} each. Go back and pick fewer friends.` (`{n}` angka, `friends` jamak; kalimat ini tidak pernah muncul dengan 1 teman karena total sudah minimal `{minimum}`). Gaya: ikon `ph_warning_circle` 16dp + `bodyMedium` `error`, letaknya sama dengan kalimat 0.9.0 `Your friends' shares add up to more than the bill.` Kalimat 0.9.0 itu ikut mendapat ikon yang sama supaya kedua pesan seragam. Panah kembali sudah membawa ke `Who's in?` dengan pilihan teman tetap tercentang.

**Tombol menempel**, diperiksa berurutan:

| Keadaan | Label | Aktif |
|---|---|---|
| Ada bagian di bawah `{minimum}` | `Minimum {minimum} a share` | tidak |
| Jumlah bagian teman melebihi total (0.9.0) | `Send {n} requests` / `Send 1 request` + kalimat merah di atas | tidak |
| Lainnya | `Send {n} requests` / `Send 1 request` | ya |
| Mengirim | spinner menggantikan label (tetap) | tidak |

- **Bagian awal** tetap dibagi rata dan dibulatkan ke bawah (0.9.0). Bila hasil bagi rata di bawah `{minimum}`, baris langsung tampil dengan tanda di atas. Aplikasi tidak menaikkan bagian sendiri, karena itu bisa membuat jumlahnya melebihi total tanpa nasabah sadar.
- **Your share** tidak berubah (termasuk tanda minus dari c225570). Bagian sendiri boleh `Rp0` atau berapa pun, karena tidak menjadi permintaan.
- **TalkBack:** setiap baris teman satu node, role Button, `onClickLabel` `Change share`. Label: `{Sinta Maharani}, {Rp5,000}` ditambah `, Needs at least {Rp10,000}` bila ditandai. Kalimat terlalu kecil adalah live region polite, diumumkan sekali saat muncul. Tombol nonaktif dibaca dengan labelnya.
- **Terang/gelap:** `error` di atas `surface`: 5.62:1 (terang) dan 8.04:1 (gelap), lolos AA untuk teks 14sp. Tidak ada token baru.
- **Font 200%:** baris kedua membungkus di bawah nama; nominal turun ke bawah subjudul seperti ListRow besar (MASTER List row). Kalimat terlalu kecil membungkus dan area isi menggulir; tombol tetap menempel.
- **Sentuhan:** baris tinggi minimal 64dp (avatar 40dp + padding 12dp), jarak antar baris 8dp (tetap).

### 2.4 Sheet ubah bagian

| Bagian | Isi |
|---|---|
| Judul | `{Sinta}'s share` (tetap) |
| Kalimat bantu (baru) | `The smallest share is {minimum}.` `bodyMedium`, `textSecondary`, tepat di bawah judul |
| Nominal | AmountDisplay + Quick Amount (hanya chip `>= {minimum}`) |
| Keypad | tetap |
| Tombol | `Rp0` → nonaktif `Enter an amount`; di bawah `{minimum}` → nonaktif `Minimum {minimum}`; selain itu aktif `Save` |

- Kalimat bantu selalu tampil, jadi nasabah tahu batasnya sebelum mengetik. Tidak ada error inline tambahan (pola 4.7).
- Sheet tidak memeriksa apakah total bagian melebihi total; itu tetap tugas layar `Check the shares` setelah sheet tertutup.
- Menutup sheet tanpa `Save` membuang perubahan (tetap).
- **TalkBack:** judul dibaca saat sheet terbuka, lalu kalimat bantu. Tombol `Minimum Rp10,000, disabled`.
- **Terang/gelap:** `textSecondary` di `surface` 7.11:1 / 8.15:1.
- **Font 200%:** kalimat bantu membungkus; keypad dan tombol mengikuti 4.7.

### 2.5 Pesan server dan minimum yang berubah

Server menolak saat membuat (requirement). Teks ini juga yang dibaca APK 0.9.0 dan 0.8.1 di kotak error mereka, jadi harus jelas tanpa konteks lain.

| Tempat di server (`requests.ts`) | Sebelum | Sesudah |
|---|---|---|
| Minta uang, nominal di bawah minimum | `Ask for at least Rp1,000.` | `Ask for at least {minimum}.` |
| Split, total di bawah minimum | `Ask for at least Rp1,000.` | `The smallest bill you can split is {minimum}.` |
| Split, satu bagian di bawah minimum | `Ask for at least Rp1,000.` | `The smallest share is {minimum}.` |

**Di aplikasi 0.9.1** pesan ini hanya muncul bila minimum di server naik saat nasabah sedang di tengah alur:

- `Check the request`: kotak error (4.12) di atas `Ask for {Rp…}` berisi pesan server. Aplikasi membaca ulang `{minimum}`. Kembali ke layar nominal, tombol sudah berkata `Minimum {minimum baru}`.
- `Check the shares`: kotak error di atas tombol berisi pesan server. Aplikasi membaca ulang `{minimum}`, tanda di baris dan label tombol langsung memakai nilai baru.
- Kotak error hilang saat nasabah mengubah sesuatu (4.12).
- **Offline saat mengirim:** kotak error `You're offline. Check your connection and try again.` (tetap).

### 2.6 `Split this bill` di detail transaksi

Tombol `Split this bill` hanya tampil bila nominal transaksi `>= {minimum}`. Transaksi yang lebih kecil tidak bisa dibagi dengan bagian yang sah, jadi tombolnya tidak ditawarkan. Syarat lain tetap (jenis `qris_payment`, `bill_payment`, `card_spend`, fitur `requests` menyala).

---

## 3. Q-02-F3 Detail kategori Insights memuat kartu kredit

**Masalah:** baris `Shopping` di Insights `Rp2,110,000` (tabungan + kartu kredit), detailnya hanya `Minimarket Sejahtera −Rp240,000` dari tabungan (`r2-insights-shopping-detail.png`).

**Aturan:** isi detail memakai aturan yang sama persis dengan total di baris kategori (`spending()` di `server/src/insights.ts`): bulan ini (Asia/Jakarta), rekening tabungan dan kartu kredit milik nasabah, uang keluar, jenis belanja (`SPEND_KINDS`), kategori dengan pilihan nasabah lebih dulu, tanpa transaksi yang sudah dibalik. Jadi jumlah nominal di detail selalu sama dengan angka di baris kategori. Satu permintaan ke server mengembalikan daftar gabungan (cara tepatnya diserahkan ke IT); aplikasi tidak menggabungkan dua sumber sendiri, supaya tidak pernah ada daftar setengah.

### 3.1 Alur (tidak berubah)

History → `Insights` → ketuk baris kategori → detail kategori → ketuk baris → detail transaksi. Back mundur satu langkah.

### 3.2 Layar

```
┌───────────────────────────────────────────┐
│ ←                                          │
│ Shopping                                   │
│ This month, from ORVYN Everyday and your   │
│ cards.                                     │
│ (▣) Tokopedia                 −Rp850,000  │
│     ORVYN Voyage ·••• 4821 · Thu, 15 Oct, │
│     20:12                                  │
│ ───────────────────────────────────────── │
│ (⌗) Minimarket Sejahtera      −Rp240,000  │
│     QRIS · Sun, 11 Oct, 11:40              │
│ ───────────────────────────────────────── │
└───────────────────────────────────────────┘
```

| Bagian | Isi |
|---|---|
| Judul | `{Shopping}` (label kategori, tetap) |
| Subjudul | `This month, from ORVYN Everyday and your cards.` (menggantikan `This month, from ORVYN Everyday.`; sama dengan subjudul Insights `What you spent from ORVYN Everyday and your cards.`) |
| Daftar | TransactionRow biasa, terbaru di atas, tabungan dan kartu tercampur menurut waktu |
| Penanda sumber, baris kartu | Ikon tile `ph_credit_card` (sudah dari jenis `card_spend`) dan subjudul baris yang diawali nama kartu dan 4 digit terakhir, yaitu `channel` yang sama dengan yang tampil di History dan Cards (`ORVYN Voyage ·••• 4821`), lalu ` · {hari}, {jam}` |
| Penanda sumber, baris tabungan | Tidak berubah: ikon menurut jenis dan `{QRIS} · {hari}, {jam}`. Tanpa nama kartu berarti dari ORVYN Everyday, seperti yang dikatakan subjudul |
| Nominal | `−Rp…` warna `text` di kedua sumber (MASTER Money colors) |
| Ketuk baris kartu | Membuka detail transaksi dari sisi kartu kredit, seperti dari Cards (17.2, `tx/{id}?account={id rekening kartu}`), jadi judul, tanda, dan baris `Account` sama dengan baris yang diketuk |
| Ketuk baris tabungan | Detail transaksi seperti sekarang |
| Banyak baris | Seluruh transaksi bulan ini tampil. Bila server membagi per halaman, ujung daftar memuat halaman berikutnya seperti History (Q-F3), termasuk baris error `Try again` di ujung daftar |

Kenapa baris tabungan tidak diberi label `ORVYN Everyday`: subjudul baris akan menjadi tiga bagian dan lebih sering membungkus di 200%, padahal nama kartu sudah cukup membedakan kedua sumber. Kartu debit memotong dari tabungan dan sudah tampil di detail ini sejak 0.8.x dengan nama `ORVYN Debit`, jadi pola penanda ini sudah dikenal.

### 3.3 State

| State | Tampilan |
|---|---|
| Memuat | Skeleton tetap seperti 0.9.0 (muncul setelah 300ms) |
| Kosong | MessageState ikon `ph_tray`, judul `No {Shopping} spending this month.` (tetap, 9.11). Sekarang hanya muncul bila tabungan dan kartu sama-sama kosong, jadi tidak pernah bertentangan dengan Insights. Layar kategori dibuka dari baris dengan total > 0, jadi keadaan ini praktis hanya muncul bila transaksi dibalik di antara dua layar |
| Error | Error state MASTER, judul `Couldn't load these` (tetap), pesan server, tombol `Try again` |
| Offline | Error state yang sama dengan pesan `You're offline. Check your connection and try again.` Tidak ada daftar dari cache, karena daftar tanpa kartu akan kembali tidak cocok dengan total |
| Sukses | Daftar di atas |

### 3.4 Aksesibilitas

- **TalkBack, baris kartu:** satu node, dibaca `{Tokopedia}, {ORVYN Voyage} card ending {4821}, {Thu, 15 Oct}, {20:12}, {−Rp850,000}`. Bagian `·•••` tidak pernah dibacakan sebagai titik atau bullet; label aksesibilitas memakai `card ending`. Aturan ini juga berlaku di setiap tempat lain yang menampilkan nomor kartu bertopeng (History, Cards, Inbox), dan ditambahkan ke MASTER.
- **TalkBack, baris tabungan:** tetap, `{Minimarket Sejahtera}, {QRIS}, {Sun, 11 Oct}, {11:40}, {−Rp240,000}`.
- **Tanpa warna:** sumber dibedakan oleh teks (nama kartu) dan bentuk ikon (`ph_credit_card`), bukan warna. Ikon tile memakai `primaryContainer` / `onPrimaryContainer` di kedua sumber.
- **Terang/gelap:** token yang sama dengan History. Subjudul baris `textSecondary` 7.11:1 (terang) dan 8.15:1 (gelap). Subjudul layar sama.
- **Font 200%:** nama kartu dan waktu membungkus ke baris berikutnya, nominal turun ke bawah subjudul (MASTER List row). Nama kartu tidak dipotong.
- **Sentuhan:** baris 64dp minimum (tetap).

---

## 4. Q-02-F1 Aksi keyboard di semua field

**Aturan:** MASTER Text field dan desain 0.9.0 bagian 17.3, tanpa perubahan isi. Ringkasnya: Next memindahkan fokus ke field berikutnya; Done di field terakhir menjalankan tombol utama bila aktif dan tidak ada pertanyaan lain di bawahnya, selain itu hanya menutup keyboard; Enter di keyboard fisik sama dengan aksi keyboard. Done tidak pernah melewati PIN: bila tombolnya membuka PIN pad, Done juga hanya membuka PIN pad.

Satu klarifikasi yang ditambahkan ke MASTER: field yang punya tombolnya sendiri tepat di bawahnya (misalnya `Find`) menjalankan tombol itu, dan field pencarian memakai aksi **Search** yang hanya menutup keyboard karena hasil sudah tersaring saat mengetik.

| Layar | Field | Aksi | Done / Enter / Search |
|---|---|---|---|
| Sign in | `Phone number` | Next | fokus ke `Password` |
| Sign in | `Password` | Done | `Sign in` bila aktif |
| Transfer → `New recipient` | `Account number` | Done | `Check account` bila aktif (temuan QA) |
| Transfer → `New recipient` | `Find a bank` | Search | tutup keyboard |
| Transfer → pilih penerima | pencarian `Search name or account number` | Search | tutup keyboard |
| Transfer → periksa | `Note (optional)` | Done | tombol `Send {Rp…}`, yang membuka PIN pad |
| Sukses Transfer → sheet simpan penerima | `Nickname` | Done | `Save` |
| Pay → PLN, BPJS, pulsa, e-wallet | nomor (`Meter number` dan lainnya) | Done | `Continue` bila aktif (temuan QA) |
| Minta uang / split → pilih orang | `ORVYN account number` | Done | `Find` bila 10 digit |
| `Check the request` | `Note (optional)` | Done | `Ask for {Rp…}` |
| `Check the shares` | `What for (optional)` | Done | `Send {n} requests` bila aktif |
| History | pencarian `Search name, note or amount` | Search | tutup keyboard |
| Profile → `Change password` | `Current password` | Next | fokus ke `New password` |
| Profile → `Change password` | `New password` | Done | `Save password` bila aktif |
| Profile → Help | `Search` | Search | tutup keyboard |
| Pendaftaran, Pockets | (semua) | | sudah benar di 0.9.0 (17.3), tetap |
| Vyna | kolom tanya (beberapa baris) | | tidak berubah: Enter membuat baris baru, kirim lewat tombol `Send` |

- **Copy:** tidak ada teks baru. Label tombol keyboard berasal dari keyboard Android.
- **Terang/gelap, TalkBack, 200%:** sama dengan 17.3. Fokus dan pengguliran mengikuti komponen Text field.

---

## 5. Q-02-F5 Snackbar di layar dengan bottom nav

**Aturan (4.11, MASTER Snackbar):** snackbar tepat di atas area aksi yang menempel dan tidak menutupi apa pun.

**Masalah:** patokan snackbar adalah tepi atas bar (`snackAnchor` di `BottomBar`), tetapi tombol QRIS bundar dinaikkan 20dp dan menonjol sekitar 16dp di atas bar (20dp di font besar). Margin snackbar 12dp, jadi tepi bawahnya menutupi 4 sampai 8dp bagian atas tombol QRIS (`r2-f2-snackbar-home-offline.png`).

**Keputusan:** di layar dengan bottom nav (Home, History, Cards, Profile), patokan snackbar adalah **tepi atas tombol QRIS yang terlihat** (posisi setelah dinaikkan), bukan tepi atas bar.

```
│ … isi beranda …                            │
│ ┌───────────────────────────────────────┐ │
│ │ ⚠ You're offline. Try again when      │ │  snackbar
│ │   you're connected.                   │ │
│ └───────────────────────────────────────┘ │
│              12dp                          │
│                 ┌────────┐                 │  tepi atas QRIS = patokan
│ ────────────────│  QRIS  │──────────────── │  tepi atas bar
│  Home  History  └────────┘  Cards  Profile │
│                    QRIS                    │
```

- Jarak tepi bawah snackbar ke tepi atas tombol QRIS: 12dp (margin snackbar yang sudah ada). Tidak ada ukuran baru.
- Snackbar boleh menutupi isi yang bisa digulir di belakangnya selama tampil (4 atau 10 detik). Isi itu tetap bisa digulir.
- Layar tanpa bottom nav tidak berubah: patokan tetap area menempel (tombol utama, keypad) atau 76dp dari bawah.
- **Font besar:** label bottom nav berhenti di 1,5×, tinggi tonjolan tombol QRIS tetap dalam dp, jadi patokan ikut posisi terukur tombol. Teks snackbar membungkus dan snackbar tumbuh ke atas.
- **Terang/gelap:** tidak berubah (`inverseSurface`). Bayangan tombol QRIS (6dp) tidak boleh terpotong oleh snackbar di tema terang.
- **TalkBack:** tidak berubah (polite, tidak mengambil fokus). Urutan fokus bottom nav tidak terpengaruh.
- **Catatan untuk IT:** posisi yang diukur harus posisi tombol QRIS setelah `raise(20.dp)`, bukan slot tata letaknya.

---

## 6. Perubahan `design-system/MASTER.md`

- **Amount input:** contoh label minimum untuk nominal per orang: `Minimum Rp10,000 a share`. Chip Quick Amount di bawah minimum tidak ditampilkan (sudah tertulis, ditegaskan berlaku juga untuk minimum yang dibaca dari server).
- **Text field:** field dengan tombol sendiri tepat di bawahnya menjalankan tombol itu; field pencarian memakai aksi Search yang hanya menutup keyboard; field beberapa baris memakai Enter untuk baris baru.
- **List row:** nomor kartu bertopeng dibacakan TalkBack sebagai `{card name} card ending {4821}`, tidak pernah sebagai titik atau bullet. Daftar yang mencampur sumber menyebut kartunya di subjudul baris.
- **Snackbar:** di layar dengan bottom nav, patokannya tepi atas tombol QRIS yang menonjol.
- Baris `Last change` diperbarui ke 0.9.1.

---

## 7. Catatan untuk PO

1. **Minimum yang naik setelah permintaan dibuat.** Bila admin menaikkan minimum transfer (misalnya ke Rp15,000), permintaan terbuka Rp10,000 sampai Rp14,999 tidak bisa dibayar, dan pembayar melihat `The smallest transfer is Rp15,000.` di layar periksa. Requirement tidak membahas ini (produksi tidak punya permintaan di bawah minimum sekarang). Bila PO ingin pesan yang lebih jelas untuk pembayar, CX mengusulkan teks server: `This request is under the {minimum} minimum, so you can't pay it. Ask {Sinta} to send a new one.` Tanpa keputusan PO, perilaku dan teks sekarang tetap.
2. **Total split minimal `{minimum}`** (bukan 2 × `{minimum}`), supaya hanya ada satu angka minimum. Akibatnya total Rp10,000 sampai Rp19,999 dengan 1 teman dimulai dengan bagian di bawah minimum dan nasabah perlu mengubah bagiannya (2.3). Bila PO memilih 2 × `{minimum}`, cukup ganti label total di 2.2 menjadi `Minimum {2 × minimum}`.

---

## 8. Cek Gerbang 2

- [x] Setiap temuan dengan tampilan (F1, F2, F3, F5) punya layar, alur, dan state. F4 hanya server.
- [x] Copy baru lengkap dan sudah melewati `humanizer`: `Minimum {minimum}`, `Minimum {minimum} a share`, `Needs at least {minimum}`, `This bill is too small for {n} friends at {minimum} each. Go back and pick fewer friends.`, `The smallest share is {minimum}.`, `Ask for at least {minimum}.`, `The smallest bill you can split is {minimum}.`, `This month, from ORVYN Everyday and your cards.`, `card ending` (TalkBack). Tanpa tanda pisah panjang.
- [x] Terang dan gelap, TalkBack, target 48dp, kontras AA, arti tanpa warna, dan font 200% tercakup per bagian.
- [x] Konsisten dengan MASTER; MASTER diperbarui (bagian 6).
- [x] Tidak ada izin Android baru dan tidak ada data perangkat.

Dilewati dengan sengaja: total kategori di kepala layar detail (angka di baris Insights sudah ada satu ketukan sebelumnya), dan skeleton berbentuk baris untuk detail kategori. Tambahkan bila QA atau tim bisnis memintanya.
