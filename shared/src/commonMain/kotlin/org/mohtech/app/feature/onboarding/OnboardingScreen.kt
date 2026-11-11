package org.mohtech.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.theme.RaqeemColors

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RaqeemColors.BackgroundSlate)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(48.dp))

            // Brand Logo
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(RaqeemColors.EmeraldGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ر",
                    color = RaqeemColors.BackgroundSlate,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "مرحباً بك في رقيم",
                color = RaqeemColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "المساعد الذكي الأول لتتبع المصاريف اليومية بالصوت وباللهجات العربية، محلياً بنسبة 100%",
                color = RaqeemColors.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 3 Feature Cards
            FeatureBenefitCard(
                icon = "🎙️",
                title = "سجل مصروفك في 3 ثوانٍ",
                description = "تحدث بطبيعتك: «بنزين بـ 50 شيكل» أو «قهوة بعشرة»، ورقيم يستخرج المبلغ والتصنيف فوراً."
            )

            Spacer(modifier = Modifier.height(14.dp))

            FeatureBenefitCard(
                icon = "👥",
                title = "من دفع ولمن؟ تقسيم فوري",
                description = "قل «عشاء بستين شيكل بيني وبين أحمد على حساب أحمد»، وسيتولى رقيم حساب وتوزيع الحصص."
            )

            Spacer(modifier = Modifier.height(14.dp))

            FeatureBenefitCard(
                icon = "🛡️",
                title = "100% بدون إنترنت وبخصوصية تامة",
                description = "بياناتك المالية ومقاطع صوتك لا تغادر جهازك نهائياً؛ صفر سيرفرات وصفر مخاطر سحابية."
            )

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Action Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp)
        ) {
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RaqeemColors.EmeraldGreen,
                    contentColor = RaqeemColors.BackgroundSlate
                )
            ) {
                Text(
                    text = "ابدأ الآن ➔",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FeatureBenefitCard(
    icon: String,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaqeemColors.SurfaceCard)
            .border(1.dp, RaqeemColors.SurfaceBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(RaqeemColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = RaqeemColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = RaqeemColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

