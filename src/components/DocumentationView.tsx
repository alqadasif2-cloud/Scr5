import React from 'react';
import { BookOpen, ShieldCheck, Cpu, HardDrive, CheckCircle2, AlertTriangle, Layers } from 'lucide-react';

export const DocumentationView: React.FC = () => {
  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Banner */}
      <div className="bg-gradient-to-r from-slate-900 via-slate-800 to-slate-900 rounded-2xl p-6 border border-slate-700/80 shadow-xl">
        <div className="flex items-center gap-3 mb-2">
          <span className="p-2.5 rounded-xl bg-indigo-500/20 text-indigo-400">
            <BookOpen className="w-7 h-7" />
          </span>
          <div>
            <h1 className="text-xl sm:text-2xl font-bold text-white">
              وثيقة التوافقية الشاملة ومعايير إدارة الصلاحيات (README_AR)
            </h1>
            <p className="text-xs sm:text-sm text-slate-400 mt-1">
              الدليل المرجعي الرسمي للمطورين والمهندسين لتطبيق Smart Remote Control
            </p>
          </div>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-6 pt-4 border-t border-slate-800 text-xs">
          <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
            <span className="text-slate-400 block mb-1">الحد الأدنى للواجهة (minSdk)</span>
            <strong className="text-emerald-400 text-sm">Android 5.0 (API 21)</strong>
          </div>
          <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
            <span className="text-slate-400 block mb-1">الحد الأدنى للتحكم باللمس</span>
            <strong className="text-indigo-400 text-sm">Android 7.0 (API 24)</strong>
          </div>
          <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
            <span className="text-slate-400 block mb-1">الإصدار المستهدف (targetSdk)</span>
            <strong className="text-purple-400 text-sm">Android 15 (API 35)</strong>
          </div>
          <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
            <span className="text-slate-400 block mb-1">معيار أجهزة البلوتوث</span>
            <strong className="text-cyan-400 text-sm">Android 12+ (API 31)</strong>
          </div>
        </div>
      </div>

      {/* Section 1: Scope Analysis */}
      <div className="bg-slate-900/80 rounded-2xl p-6 border border-slate-800 space-y-4">
        <h2 className="text-lg font-bold text-white flex items-center gap-2 border-b border-slate-800 pb-3">
          <Cpu className="w-5 h-5 text-indigo-400" />
          <span>10.1 تحديد نطاق التوافق والتقنيات المستخدمة</span>
        </h2>
        
        <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
          تم فحص متطلبات كافة التقنيات الأساسية، وتم اختيار أقل إصدار Android يمكن أن يدعم الوظائف بصورة مستقرة:
        </p>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
          <div className="p-4 bg-slate-950/70 rounded-xl border border-slate-800 space-y-2">
            <strong className="text-indigo-300 block text-sm">مشاركة وبث الشاشة (MediaProjection)</strong>
            <p className="text-slate-300">
              مدعومة رسمياً منذ Android 5.0 (API 21). تتطلب موافقة المستخدم في كل جلسة، وتتطلب على Android 10+ و Android 14+ تصنيف الخدمة كـ <code className="text-indigo-300 font-mono">foregroundServiceType="mediaProjection"</code> مع تشغيل الخدمة وربط الإشعار أولاً قبل بدء الجلسة.
            </p>
          </div>

          <div className="p-4 bg-slate-950/70 rounded-xl border border-slate-800 space-y-2">
            <strong className="text-emerald-300 block text-sm">التحكم باللمس والإيماءات (Accessibility)</strong>
            <p className="text-slate-300">
              حقن اللمس الحر بالإحداثيات (dispatchGesture) ظهر لأول مرة في <code className="text-emerald-300 font-mono">Android 7.0 (API 24)</code>. على الإصدارات الأقدم (API 21-23) يتم التحول التلقائي لمحاكاة أزرار النظام (Back/Home/Recents) ومؤشر الفأرة اللاسلكي لتفادي الانهيار.
            </p>
          </div>

          <div className="p-4 bg-slate-950/70 rounded-xl border border-slate-800 space-y-2">
            <strong className="text-amber-300 block text-sm">اكتشاف البلوتوث وإدارته</strong>
            <p className="text-slate-300">
              في Android 12+ (API 31+) نستخدم <code className="text-amber-300 font-mono">BLUETOOTH_SCAN</code> مع <code className="text-amber-300 font-mono">neverForLocation</code> لحماية خصوصية الموقع. في الإصدارات السابقة (API 23-30) يلزم النظام إذن الموقع <code className="text-amber-300 font-mono">ACCESS_FINE_LOCATION</code>.
            </p>
          </div>

          <div className="p-4 bg-slate-950/70 rounded-xl border border-slate-800 space-y-2">
            <strong className="text-purple-300 block text-sm">الخدمات الأمامية والإشعارات</strong>
            <p className="text-slate-300">
              في Android 8.0+ يلزم إنشاء NotificationChannel. في Android 13+ (API 33) تم تحويل الإشعارات إلى إذن وقت تشغيل <code className="text-purple-300 font-mono">POST_NOTIFICATIONS</code>، ونحرص على عدم طلبه على الأجهزة السابقة لـ API 33.
            </p>
          </div>
        </div>
      </div>

      {/* Section 2: Distinction between Core and Functional */}
      <div className="bg-slate-900/80 rounded-2xl p-6 border border-slate-800 space-y-4">
        <h2 className="text-lg font-bold text-white flex items-center gap-2 border-b border-slate-800 pb-3">
          <Layers className="w-5 h-5 text-emerald-400" />
          <span>11. الفصل الصارم بين التوافق الأساسي والتوافق الوظيفي</span>
        </h2>

        <div className="space-y-3 text-xs sm:text-sm text-slate-300 leading-relaxed">
          <div className="p-4 bg-emerald-950/20 rounded-xl border border-emerald-500/30">
            <strong className="text-emerald-400 font-bold block mb-1">
              أولاً: التوافق الأساسي (Core Compatibility - يبدأ من API 21)
            </strong>
            <p>
              يضمن تنصيب التطبيق بنجاح دون أخطاء حزمة، وظهور كافة شاشات الإعدادات، واستعراض الأجهزة المقترنة، وحفظ المفاتيح، والاتصال بالشبكة المحلية، دون أن يتعرض المستخدم لأي إغلاق إجباري (Force Close).
            </p>
          </div>

          <div className="p-4 bg-indigo-950/20 rounded-xl border border-indigo-500/30">
            <strong className="text-indigo-400 font-bold block mb-1">
              ثانياً: التوافق الوظيفي (Functional Compatibility - متدرج بحسب إمكانات النظام)
            </strong>
            <p>
              لا يعرض التطبيق ميزة غير مدعومة كأنها تعمل، ولا يُظهر أزراراً مضللة؛ فإذا كان الهاتف لا يدعم اللمس بالإحداثيات (أقل من API 24)، يتم توضيح ذلك للمستخدم وتفعيل «نمط المشاهدة والتنقل بالمفاتيح» تلقائياً.
            </p>
          </div>
        </div>
      </div>

      {/* Section 3: Permission Management Rules */}
      <div className="bg-slate-900/80 rounded-2xl p-6 border border-slate-800 space-y-4">
        <h2 className="text-lg font-bold text-white flex items-center gap-2 border-b border-slate-800 pb-3">
          <ShieldCheck className="w-5 h-5 text-indigo-400" />
          <span>10.3 القواعد العشر لإدارة الصلاحيات في Smart Remote</span>
        </h2>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
          {[
            'طلب كل صلاحية في الوقت الفعلي فقط عند حاجة المستخدم للوظيفة.',
            'شرح سبب الصلاحية باللغة العربية الواضحة قبل ظهور نافذة النظام.',
            'فحص الصلاحية قبل تشغيل أي ميزة لمنع استثناءات SecurityException.',
            'التعامل الآمن مع رفض المستخدم دون أن يتوقف التطبيق عن العمل.',
            'توفير دليل استعادة الصلاحية خطوة بخطوة عند الرفض الدائم.',
            'عدم تكرار الطلب بصورة مزعجة (Anti-Spamming Protection).',
            'عدم طلب أي صلاحية غير مستخدمة إطلاقاً في الكود.',
            'فحص إمكانات الجهاز قبل عرض الميزة للمستخدم.',
            'توفير بدائل عملية (Fallback) عندما تكون الصلاحية غير متاحة.',
            'التعامل المرن مع إلغاء المستخدم لموافقة البث أثناء عمل الجلسة.',
          ].map((rule, idx) => (
            <div key={idx} className="flex items-start gap-2.5 p-3 bg-slate-950/60 rounded-xl border border-slate-800">
              <span className="w-5 h-5 rounded-full bg-indigo-500/20 text-indigo-400 flex items-center justify-center font-bold text-[11px] shrink-0">
                {idx + 1}
              </span>
              <span className="text-slate-300 pt-0.5">{rule}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
