package com.example.ui.screens.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ServiceLink(
    val name: String,
    val desc: String,
    val url: String,
    val emoji: String
)

data class ServiceCategory(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val links: List<ServiceLink>
)

val FOKOONE_CATEGORIES = listOf(
    ServiceCategory(
        id = "gov",
        title = "دولت و پیشخوان الکترونیک",
        icon = Icons.Default.AccountBalance,
        color = Color(0xFF6C3CE1),
        links = listOf(
            ServiceLink("پنجره ملی دولت هوشمند", "یارانه، سهام عدالت، خودرو، مدارک", "https://my.gov.ir", "🪟"),
            ServiceLink("پلیس +۱۰", "گذرنامه، گواهینامه، تعویض پلاک", "https://epolice.ir", "🚔"),
            ServiceLink("سامانه عدل‌ایران (ثنا)", "ابلاغیه الکترونیک، پیگیری پرونده قضایی", "https://adliran.ir", "⚖️"),
            ServiceLink("سازمان تأمین اجتماعی", "سوابق بیمه، فیش حقوقی و بازنشستگی", "https://tamin.ir", "🛡️"),
            ServiceLink("خدمات غیرحضوری تأمین", "سامانه جامع es.tamin", "https://es.tamin.ir", "💻"),
            ServiceLink("پورتال درمان تأمین اجتماعی", "خدمات درمانی و نوبت‌دهی بیمارستان‌ها", "https://daman.tamin.ir", "🏥"),
            ServiceLink("نسخه الکترونیک", "مشاهده اقلام دارویی و نسخه‌های تجویزی", "https://rx.tamin.ir", "💊"),
            ServiceLink("امور مالیاتی کشور", "اظهارنامه، مالیات مشاغل و مستغلات", "https://tax.gov.ir", "🧾"),
            ServiceLink("مالیات بر ارزش افزوده", "استعلام و پرداخت مالیات ارزش افزوده", "https://evat.tax.gov.ir", "💰"),
            ServiceLink("ثبت اسناد و املاک کشور", "استعلام سند، ثبت شرکت و شناسه ملی", "https://ssaa.ir", "📜"),
            ServiceLink("ثبت برند و مالکیت معنوی", "استعلام و ثبت علامت تجاری و اختراع", "https://sabtemark.ssaa.ir", "™️"),
            ServiceLink("گمرک جمهوری اسلامی ایران", "ترخیص کالا، رجیستری و گمرکات", "https://irica.gov.ir", "🚢"),
            ServiceLink("سازمان ثبت احوال کشور", "کارت ملی هوشمند، تعویض شناسنامه", "https://sabtahval.ir", "🪪"),
            ServiceLink("سامانه خودنویس املاک", "ثبت رایگان قرارداد اجاره و مبایعه‌نامه", "https://khodnevis.mrud.ir", "✍️"),
            ServiceLink("سامانه املاک و اسکان", "ثبت اطلاعات سکونت و مالکیت", "https://amlak.mrud.ir", "🏠"),
            ServiceLink("سازمان نظام مهندسی", "استعلام پروانه و ارجاع نظارت", "https://nogh.ir", "🏗️"),
            ServiceLink("سازمان نظام پزشکی", "استعلام اصالت شماره نظام پزشکی", "https://irc.ir", "⚕️"),
            ServiceLink("پیگیری کارت سوخت", "رهگیری و صدور کارت سوخت المثنی", "https://noori.niopdc.ir", "⛽"),
            ServiceLink("رهگیری مرسولات پستی", "پیگیری بارکد ۲۰ رقمی مرسولات", "https://tracking.post.ir", "📦"),
            ServiceLink("سامانه شمیم دهک‌بندی", "استعلام وضعیت دهک‌بندی و یارانه", "https://shahed.gov.ir", "📊"),
            ServiceLink("سامانه پلیس من", "استعلام خلافی، نمره منفی و پلاک", "https://police.ir", "👮"),
            ServiceLink("آموزش فنی و حرفه‌ای", "ثبت‌نام دوره‌ها و دریافت گواهینامه", "https://portaltvto.com", "🎓")
        )
    ),
    ServiceCategory(
        id = "finance",
        title = "بانک‌ها، پرداخت و خدمات مالی",
        icon = Icons.Default.Business,
        color = Color(0xFF0061A4),
        links = listOf(
            ServiceLink("بانک مرکزی جمهوری اسلامی", "نرخ رسمی ارز، طلا و بخشنامه‌ها", "https://cbi.ir", "🏛️"),
            ServiceLink("سامانه صیاد (بانک مرکزی)", "ثبت، تایید، استعلام و انتقال چک", "https://sayad.co.ir", "📋"),
            ServiceLink("سامانه چکاوک", "استعلام سابقه چک برگشتی", "https://chekav.com", "🔴"),
            ServiceLink("بانک ملی ایران (بام)", "همراه بانک و خدمات الکترونیک", "https://bmi.ir", "🏦"),
            ServiceLink("بانک ملت", "سامانه همراه بانک و بانکداری اینترنتی", "https://bankmellat.ir", "🌐"),
            ServiceLink("بانک تجارت", "خدمات همراه بانک و تسهیلات", "https://tejaratbank.ir", "💼"),
            ServiceLink("بانک صادرات ایران", "خدمات ارزی، ریالی و همراه بانک", "https://bsi.ir", "📤"),
            ServiceLink("بانک سپه", "نخستین بانک ایرانی و خدمات آنلاین", "https://sb24.ir", "🏛️"),
            ServiceLink("بانک پاسارگاد", "بانکداری دیجیتال و ویپاد", "https://pasargadbank.ir", "🏛️"),
            ServiceLink("بلو بانک (سامان)", "بانک تمام دیجیتال و نئوبانک", "https://blubank.com", "💙"),
            ServiceLink("بانک مسکن", "افتتاح حساب، تسهیلات و وام مسکن", "https://maskan-bank.ir", "🏠"),
            ServiceLink("بانک رفاه کارگران", "خدمات بانکی مستمری‌بگیران", "https://refah-bank.ir", "❤️"),
            ServiceLink("بانک کشاورزی", "خدمات اعتباری بخش کشاورزی", "https://bki.ir", "🌾"),
            ServiceLink("بانک سامان", "خدمات ارزی، نت‌بانک و موبایلت", "https://samanehbank.ir", "💎"),
            ServiceLink("بانک شهر", "خدمات شهری و پیشخوان‌های شهرنت", "https://bankshahr.ir", "🏙️"),
            ServiceLink("شاپرک", "شبکه الکترونیکی پرداخت کارتی", "https://shaparak.ir", "💳"),
            ServiceLink("آپ (آسان پرداخت)", "خرید شارژ، پرداخت قبض و کارت به کارت", "https://asapardakht.ir", "💳"),
            ServiceLink("دیجی‌پی", "خرید اقساطی، پرداخت و وام آنلاین", "https://digipay.ir", "💳"),
            ServiceLink("تومن", "کیف پول الکترونیک و پرداخت خرد", "https://tomanapp.ir", "💰")
        )
    ),
    ServiceCategory(
        id = "stock",
        title = "بورس، طلا و بازار سرمایه",
        icon = Icons.Default.TrendingUp,
        color = Color(0xFF006D39),
        links = listOf(
            ServiceLink("مدیریت فناوری بورس (TSETMC)", "داده‌های زنده معاملات بورس تهران", "https://tsetmc.com", "📊"),
            ServiceLink("سامانه کدال (Codal)", "گزارش‌های مالی و افشای اطلاعات شرکت‌ها", "https://codal.ir", "📑"),
            ServiceLink("شبکه طلا و ارز (TGJU)", "قیمت لحظه‌ای طلا، سکه، دلار و تتر", "https://tgju.org", "🥇"),
            ServiceLink("سامانه جامع سجام", "احراز هویت و دریافت سود سهام", "https://ddn.ir", "🪪"),
            ServiceLink("سازمان بورس و اوراق بهادار", "پایگاه اخبار و مقررات بازار سرمایه", "https://seobourse.ir", "🏦")
        )
    ),
    ServiceCategory(
        id = "health",
        title = "پزشکی، سلامت و نوبت‌دهی",
        icon = Icons.Default.LocalHospital,
        color = Color(0xFFBA1A1A),
        links = listOf(
            ServiceLink("نوبت دات آی‌آر", "رزرو اینترنتی مطب پزشکان سراسر کشور", "https://nobat.ir", "🩺"),
            ServiceLink("پذیرش ۲۴", "مشاوره آنلاین و نوبت‌دهی پزشکان و درمانگاه", "https://paziresh24.com", "👨‍⚕️"),
            ServiceLink("دکتردکتر", "سامانه جامع نوبت‌دهی و ثبت نظر بیماران", "https://doctordoctor.ir", "🔬"),
            ServiceLink("اسنپ دکتر", "مشاوره تلفنی و متنی با پزشکان و روانشناس", "https://snapp.doctor", "💊"),
            ServiceLink("سازمان غذا و دارو", "استعلام داروهای کمیاب و تاییدیه سلامت", "https://fda.gov.ir", "💉"),
            ServiceLink("سامانه تیتک (TTAC)", "کنترل اصالت دارو، تجهیزات و مکمل‌ها", "https://ttac.ir", "🔎"),
            ServiceLink("بیمه سلامت ایرانیان", "استعلام بیمه همگانی و تمدید دفترچه", "https://salamat.gov.ir", "❤️"),
            ServiceLink("سامانه بهدارا", "پرونده الکترونیک سلامت شهروندی", "https://behdara.behdasht.gov.ir", "📋")
        )
    ),
    ServiceCategory(
        id = "transport",
        title = "سفر، حمل‌ونقل و رزرو بلیط",
        icon = Icons.Default.Flight,
        color = Color(0xFF00897B),
        links = listOf(
            ServiceLink("پلیس راهور ۱۲۰", "استعلام خلافی خودرو، گواهینامه و نمره منفی", "https://rahvar120.ir", "🚗"),
            ServiceLink("سامانه جامع ۱۴۱", "وضعیت ترافیک، دوربین‌های جاده‌ای و هواشناسی", "https://141.ir", "🛣️"),
            ServiceLink("راه‌آهن جمهوری اسلامی (رجا)", "خرید اینترنتی بلیط قطارهای سراسری", "https://raja.ir", "🚂"),
            ServiceLink("علی‌بابا", "خرید بلیط پرواز داخلی/خارجی، قطار، اتوبوس و هتل", "https://alibaba.ir", "🎫"),
            ServiceLink("اسنپ تریپ", "رزرو هتل، ویلا و بلیط هواپیما", "https://snapptrip.com", "🏨"),
            ServiceLink("فلای تودی", "خرید آنلاین ارزان‌ترین بلیط پرواز و هتل", "https://flytoday.ir", "🛫"),
            ServiceLink("جاباما", "رزرو آنلاین اقامتگاه، بوم‌گردی و ویلا", "https://chabama.ir", "🏡"),
            ServiceLink("فرودگاه بین‌المللی امام خمینی", "برنامه پروازهای ورودی و خروجی خارجی", "https://ikac.ir", "🛫"),
            ServiceLink("فرودگاه بین‌المللی مهرآباد", "اطلاعات پروازهای داخلی تهران", "https://mhr.airport.ir", "🛬"),
            ServiceLink("متروی تهران و حومه", "نقشه خطوط، زمان‌بندی و نرخ کرایه", "https://metro.tehran.ir", "🚇"),
            ServiceLink("سامانه طرح ترافیک تهران", "رزرو تردد و پرداخت عوارض طرح ترافیک", "https://tarh.tehran.ir", "🚦")
        )
    ),
    ServiceCategory(
        id = "city",
        title = "تاکسی آنلاین و مسیریابی شهری",
        icon = Icons.Default.DirectionsBus,
        color = Color(0xFF0288D1),
        links = listOf(
            ServiceLink("اسنپ (Snapp)", "سامانه هوشمند حمل‌ونقل مسافر و بار", "https://snapp.ir", "🚕"),
            ServiceLink("تپسی (Tapsi)", "درخواست خودرو، پیک و سفرهای بین‌شهری", "https://tapsi.ir", "🚖"),
            ServiceLink("الوپیک (Alopeyk)", "پیک موتوری و وانت‌بار آنلاین", "https://alopeyk.ir", "🏍️"),
            ServiceLink("مسیریاب نشان", "نقشه سخنگوی فارسی با ترافیک زنده و دوربین‌ها", "https://neshan.org", "📍"),
            ServiceLink("مسیریاب بلد", "نقشه و جهت‌یاب جامع شهری ایران", "https://balad.ir", "🗺️")
        )
    ),
    ServiceCategory(
        id = "shop",
        title = "فروشگاه‌ها و نیازمندی‌های آنلاین",
        icon = Icons.Default.ShoppingCart,
        color = Color(0xFFE65100),
        links = listOf(
            ServiceLink("دیجی‌کالا", "بزرگ‌ترین فروشگاه آنلاین کالا در ایران", "https://digikala.com", "🛍️"),
            ServiceLink("دیوار", "مرجع ثبت رایگان آگهی‌های خرید، فروش، املاک و خودرو", "https://divar.ir", "📌"),
            ServiceLink("شیپور", "نیازمندی‌های آنلاین و بازار دست دوم", "https://sheypoor.com", "📣"),
            ServiceLink("ترب", "موتور جستجو و مقایسه کمترین قیمت فروشگاه‌ها", "https://torob.com", "🔍"),
            ServiceLink("ایمالز", "پیگیری قیمت و مقایسه کالاهای دیجیتال", "https://emalls.ir", "📊"),
            ServiceLink("باسلام", "بازار اجتماعی خرید مستقیم از تولیدکنندگان خانگی", "https://basalam.com", "🧺"),
            ServiceLink("رینگ (Ring)", "سامانه ثبت رایگان آگهی‌های کسب‌وکار", "https://ring.ir/landing", "🔔"),
            ServiceLink("تکنولایف", "فروش تخصصی موبایل، لپ‌تاپ و گجت‌های هوشمند", "https://technolife.ir", "📱"),
            ServiceLink("اوکالا (Okala)", "خرید آنلاین خواروبار از فروشگاه‌های افق کوروش", "https://okala.com", "🥫"),
            ServiceLink("اسنپ اکسپرس", "تحویل فوری سوپرمارکت زیر ۳۰ دقیقه", "https://snapp.express", "🚀"),
            ServiceLink("بانی مد", "مرکز خرید آنلاین پوشاک و برندهای معتبر", "https://banimode.com", "👕")
        )
    ),
    ServiceCategory(
        id = "entertainment",
        title = "فیلم، سریال، ویدیو و سرگرمی",
        icon = Icons.Default.Movie,
        color = Color(0xFF7B1FA2),
        links = listOf(
            ServiceLink("فیلیمو", "تماشای آنلاین هزاران فیلم، سریال و انیمیشن", "https://filimo.com", "🎥"),
            ServiceLink("نماوا", "پلتفرم پخش آنلاین فیلم و سریال‌های اختصاصی", "https://namava.ir", "📺"),
            ServiceLink("فیلم‌نت", "تماشای سریال‌های روز و برنامه‌های اختصاصی", "https://filmnet.ir", "🎞️"),
            ServiceLink("تلوبیون", "پخش زنده و آرشیو کامل تمام شبکه‌های صداوسیما", "https://telewebion.com", "📺"),
            ServiceLink("آپارات", "پلتفرم اشتراک‌گذاری ویدیوی ایرانی", "https://aparat.com", "▶️"),
            ServiceLink("سینماتیکت", "خرید آنلاین بلیت سینماها و تئاتر سراسر کشور", "https://cinematicket.ir", "🎟️"),
            ServiceLink("ورزش ۳", "پایگاه خبری و نتایج زنده مسابقات ورزشی", "https://varzesh3.com", "⚽"),
            ServiceLink("رادیو جوان", "مرجع دانلود موسیقی و پادکست‌های فارسی", "https://radiojavan.com", "🎵")
        )
    ),
    ServiceCategory(
        id = "telecom",
        title = "اپراتورها، اینترنت و شارژ",
        icon = Icons.Default.Language,
        color = Color(0xFF1565C0),
        links = listOf(
            ServiceLink("همراه اول (همراه من)", "خرید بسته اینترنت، شارژ و پرداخت قبض", "https://mci.ir", "📱"),
            ServiceLink("ایرانسل (ایرانسل‌من)", "خدمات سیم‌کارت، مودم TD-LTE و بسته‌ها", "https://irancell.ir", "📡"),
            ServiceLink("رایتل", "خدمات سیم‌کارت دیتای رایتل", "https://rightel.ir", "📳"),
            ServiceLink("شرکت مخابرات ایران", "ثبت‌نام تلفن ثابت و اینترنت پرسرعت ADSL/فیبر", "https://tci.ir", "☎️"),
            ServiceLink("شاتل", "اینترنت پرسرعت خانگی و تجاری ADSL/VDSL", "https://shatel.ir", "🌐"),
            ServiceLink("آسیاتک", "خدمات پهنای باند و اینترنت پرسرعت", "https://asiatech.ir", "🔌"),
            ServiceLink("زیتل (Zitel)", "اینترنت نامحدود بی‌سیم شهری", "https://zitel.ir", "🌍")
        )
    ),
    ServiceCategory(
        id = "messenger",
        title = "پیام‌رسان‌های بومی",
        icon = Icons.Default.Apps,
        color = Color(0xFF00897B),
        links = listOf(
            ServiceLink("روبیکا (Rubika)", "سوپراپ ارتباطی، پخش فیلم و موسیقی", "https://rubika.ir", "💬"),
            ServiceLink("بله (Bale)", "پیام‌رسان بانکی و تراکنش مالی بانک ملی", "https://ble.ir", "✅"),
            ServiceLink("ایتا (Eitaa)", "پیام‌رسان سریع و کانال‌های خبری فارسی", "https://eitaa.com", "📨")
        )
    ),
    ServiceCategory(
        id = "food",
        title = "سفارش غذا و رستوران‌ها",
        icon = Icons.Default.LocalMall,
        color = Color(0xFFC2185B),
        links = listOf(
            ServiceLink("اسنپ فود", "سفارش آنلاین غذا از رستوران‌ها، شیرینی و نان", "https://snappfood.ir", "🍕"),
            ServiceLink("تپسی فود", "سفارش غذای آنلاین با تخفیف‌های ویژه", "https://tapsifood.ir", "🍱")
        )
    ),
    ServiceCategory(
        id = "news",
        title = "خبرگزاری‌ها و پایگاه‌های فناوری",
        icon = Icons.Default.Newspaper,
        color = Color(0xFFD81B60),
        links = listOf(
            ServiceLink("خبرگزاری جمهوری اسلامی (ایرنا)", "اخبار رسمی و فوری ایران و جهان", "https://irna.ir", "📰"),
            ServiceLink("خبرگزاری ایسنا", "اخبار دانشگاهی و سیاسی اجتماعی", "https://isna.ir", "📝"),
            ServiceLink("خبرگزاری تسنیم", "پایگاه تحلیلی و خبری", "https://tasnimnews.com", "📢"),
            ServiceLink("خبرگزاری مهر", "اخبار فرهنگی و بین‌المللی", "https://mehrnews.com", "🌙"),
            ServiceLink("خبرگزاری فارس", "اخبار سیاسی و اجتماعی", "https://farsnews.ir", "🏹"),
            ServiceLink("خبر آنلاین", "اخبار روز و تحلیل‌های سیاسی اقتصادی", "https://khabaronline.ir", "🌐"),
            ServiceLink("زومیت (Zoomit)", "بررسی تخصصی گوشی، لپ‌تاپ و دنیای فناوری", "https://zoomit.ir", "🔬"),
            ServiceLink("دیجیاتو (Digiato)", "اخبار استارتاپ‌ها، هوش مصنوعی و فناوری", "https://digiato.com", "💻")
        )
    ),
    ServiceCategory(
        id = "education",
        title = "آموزش آنلاین، دانشگاه‌ها و آزمون‌ها",
        icon = Icons.Default.School,
        color = Color(0xFF4527A0),
        links = listOf(
            ServiceLink("سازمان سنجش آموزش کشور", "کنکور سراسری، آزمون‌های استخدامی و ارشد", "https://sanjesh.org", "📝"),
            ServiceLink("وزارت علوم، تحقیقات و فناوری", "خدمات دانشجویی، بورسیه و ارزشیابی مدارک", "https://msrt.ir", "🏫"),
            ServiceLink("سامانه شاد", "شبکه آموزشی دانش‌آموزی وزارت آموزش و پرورش", "https://shad.ir", "📱"),
            ServiceLink("فرادرس", "بزرگ‌ترین مرجع فیلم‌های آموزشی آنلاین و دانشگاهی", "https://faradars.org", "🎬"),
            ServiceLink("مکتب‌خونه", "دوره‌های آنلاین برنامه‌نویسی، کسب‌وکار و زبان", "https://maktabkhooneh.org", "🏫"),
            ServiceLink("دانشگاه تهران", "سامانه آموزشی و اطلاع‌رسانی دانشگاه مادر", "https://ut.ac.ir", "🎓"),
            ServiceLink("دانشگاه صنعتی شریف", "پرتال دانشگاه صنعتی شریف تهران", "https://sharif.edu", "🔬"),
            ServiceLink("دانشگاه آزاد اسلامی", "سامانه آموزشیار و ثبت‌نام واحدهای دانشگاه آزاد", "https://azad.ac.ir", "🏛️")
        )
    ),
    ServiceCategory(
        id = "job",
        title = "استخدام، کاریابی و فریلنسری",
        icon = Icons.Default.Work,
        color = Color(0xFF2E7D32),
        links = listOf(
            ServiceLink("جابینجا (Jobinja)", "استخدام در بهترین شرکت‌های دانش‌بنیان و نوآور", "https://jobinja.ir", "🔎"),
            ServiceLink("ای‌استخدام (E-Estekhdam)", "آگهی‌های استخدام بانک‌ها، سازمان‌ها و شرکت‌ها", "https://e-estekhdam.com", "💼"),
            ServiceLink("ایران تلنت (IranTalent)", "کاریابی تخصصی مدیران و متخصصان ارشد", "https://irantalent.com", "🎯"),
            ServiceLink("کار دات آی‌آر", "بانک فرصت‌های شغلی سراسر کشور", "https://kar.ir", "👔")
        )
    ),
    ServiceCategory(
        id = "utilities",
        title = "ابزارهای کاربردی، تقویم و هواشناسی",
        icon = Icons.Default.Public,
        color = Color(0xFF00695C),
        links = listOf(
            ServiceLink("سازمان هواشناسی کشور", "پیش‌بینی هوای شهرها، جاده‌ها و هشدارها", "https://irimo.ir", "🌡️"),
            ServiceLink("تقویم و اوقات شرعی (Tagvim)", "تقویم جلالی، اذان و مناسبت‌های ملی", "https://tagvim.ir", "🕌"),
            ServiceLink("مرکز آمار ایران", "سرشماری، نرخ تورم و داده‌های آماری رسمی", "https://amar.org.ir", "📊"),
            ServiceLink("سامانه ویراستیار", "ویرایش آنلاین املایی و نگارشی خط فارسی", "https://virastyar.ir", "✏️"),
            ServiceLink("پیکوفایل (Picofile)", "آپلود رایگان عکس و فایل با لینک مستقیم", "https://picofile.com", "🖼️"),
            ServiceLink("کافه بازار", "دانلود میلیون‌ها برنامه و بازی اندرویدی", "https://cafebazaar.ir", "🏪"),
            ServiceLink("مایکت (Myket)", "فروشگاه بازی و اپلیکیشن اندروید ایرانی", "https://myket.ir", "📲")
        )
    )
)

@Composable
fun FokooneServicesScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>("all") }
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }

    // Live Persian Date and Time
    var currentDateTimeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            val formatter = SimpleDateFormat("EEEE، d MMMM yyyy - ساعت HH:mm:ss", Locale("fa", "IR"))
            currentDateTimeText = formatter.format(now)
            delay(1000)
        }
    }

    val filteredCategories = remember(searchQuery, selectedCategoryId) {
        val baseList = if (selectedCategoryId == null || selectedCategoryId == "all") {
            FOKOONE_CATEGORIES
        } else {
            FOKOONE_CATEGORIES.filter { it.id == selectedCategoryId }
        }

        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.mapNotNull { cat ->
                val matchingLinks = cat.links.filter { link ->
                    link.name.contains(searchQuery, ignoreCase = true) ||
                    link.desc.contains(searchQuery, ignoreCase = true) ||
                    link.url.contains(searchQuery, ignoreCase = true)
                }
                if (matchingLinks.isNotEmpty() || cat.title.contains(searchQuery, ignoreCase = true)) {
                    cat.copy(links = if (matchingLinks.isNotEmpty()) matchingLinks else cat.links)
                } else {
                    null
                }
            }
        }
    }

    val totalSitesCount = remember {
        FOKOONE_CATEGORIES.sumOf { it.links.size }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Header Card - Fokoone Branding
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fokoone_hero_banner"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0F172A)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF5228CC),
                                    Color(0xFF1E1B4B),
                                    Color(0xFF0F172A)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Fokoone Logo Badge
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF6C3CE1),
                            shadowElevation = 8.dp,
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "F1",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 24.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Title Requested: "خانه خدمات ایران"
                        Text(
                            text = "خانه خدمات ایران",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF06D6A0),
                            textAlign = TextAlign.Center
                        )

                        // Subtitle / ریزعنوان Requested: "فوکو وان" (Fokoone)
                        Text(
                            text = "فوکو وان (Fokoone)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "دسترسی مستقیم و سریع به بیش از $totalSitesCount سامانه دولتی، بانکی و کاربردی کشور",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        if (currentDateTimeText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF06D6A0).copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF06D6A0).copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = currentDateTimeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF06D6A0),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Search Box
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fokoone_search_input"),
                placeholder = {
                    Text(
                        text = "جستجوی سامانه (مثلاً: یارانه، ثبت اسناد، بورس، نوبت‌دهی...)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "جستجو",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "پاک کردن",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        // Category Filter Chips Row
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == "all",
                        onClick = { selectedCategoryId = "all" },
                        label = { Text("همه دسته‌ها ($totalSitesCount)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                items(FOKOONE_CATEGORIES, key = { it.id }) { cat ->
                    val isSelected = selectedCategoryId == cat.id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCategoryId = if (isSelected) "all" else cat.id
                        },
                        label = { Text("${cat.title} (${cat.links.size})") },
                        leadingIcon = {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        // Categories & Links List
        if (filteredCategories.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "سامانه‌ای مطابق با «$searchQuery» پیدا نشد.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredCategories, key = { it.id }) { category ->
                // Default expanded when searching or single category filtered
                val isExpanded = if (searchQuery.isNotBlank() || selectedCategoryId != "all") {
                    true
                } else {
                    expandedMap[category.id] ?: false
                }

                val arrowRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    label = "arrow"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_card_${category.id}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Category Header (Clickable for expand/collapse)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedMap[category.id] = !isExpanded
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = category.color.copy(alpha = 0.12f),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = null,
                                            tint = category.color,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = category.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${category.links.size} سامانه و پایگاه فعال",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "بستن" else "باز کردن",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(arrowRotation)
                            )
                        }

                        // Category Links
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                category.links.forEach { link ->
                                    ServiceLinkItem(
                                        link = link,
                                        onOpen = {
                                            openUrl(context, link.url)
                                        },
                                        onCopy = {
                                            clipboardManager.setText(AnnotatedString(link.url))
                                            Toast.makeText(context, "آدرس ${link.name} کپی شد", Toast.LENGTH_SHORT).show()
                                        },
                                        onShare = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, link.name)
                                                putExtra(Intent.EXTRA_TEXT, "${link.name}\n${link.desc}\n${link.url}\n(از طریق خانه خدمات ایران - Fokoone)")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری سامانه"))
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }

        // Footer Card - Fokoone Creds
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⭐ سامانه جامع فوکو وان (Fokoone) ⭐",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "طراحی و توسعه: محمد فوکولایی • دسترسی آسان و سریع به تمام درگاه‌های ایران",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceLinkItem(
    link: ServiceLink,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("service_link_${link.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Favicon Emoji Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = link.emoji,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (link.desc.isNotBlank()) {
                    Text(
                        text = link.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "کپی لینک",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "اشتراک‌گذاری",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onOpen() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "باز کردن سایت",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "خطا در باز کردن مرورگر: $url", Toast.LENGTH_SHORT).show()
    }
}
