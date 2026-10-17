import React, { useState } from 'react';
import { SOURCE_CODE_FILES, SourceCodeFile } from '../data/sourceCodeTemplates';
import { Code2, Copy, Check, FileCode, Download, ExternalLink } from 'lucide-react';

export const CodeViewer: React.FC = () => {
  const [selectedFileId, setSelectedFileId] = useState<string>(SOURCE_CODE_FILES[0].id);
  const [copied, setCopied] = useState<boolean>(false);

  const activeFile = SOURCE_CODE_FILES.find((f) => f.id === selectedFileId) || SOURCE_CODE_FILES[0];

  const handleCopy = () => {
    navigator.clipboard.writeText(activeFile.code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownload = () => {
    const blob = new Blob([activeFile.code], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = activeFile.filename;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="bg-gradient-to-r from-slate-900 via-slate-800 to-slate-900 rounded-2xl p-6 border border-slate-700/80 shadow-xl">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="p-2 rounded-lg bg-indigo-500/20 text-indigo-400">
                <Code2 className="w-6 h-6" />
              </span>
              <h2 className="text-xl sm:text-2xl font-bold text-white">
                شيفرات الإنتاج البرمجية لـ Android (Production Ready Kotlin & Gradle)
              </h2>
            </div>
            <p className="text-sm text-slate-300 max-w-3xl leading-relaxed">
              الملفات البرمجية الأصلية الكاملة والمعدّة للاستخدام المباشر داخل مشروع Android Studio، 
              وتتضمن إدارة الصلاحيات، وخدمة MediaProjection المتوافقة مع Android 14+، وخدمة Accessibility للتحكم باللمس.
            </p>
          </div>

          <div className="flex items-center gap-2 self-start md:self-center">
            <button
              onClick={handleCopy}
              className="flex items-center gap-1.5 px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs sm:text-sm font-semibold border border-slate-700 transition"
            >
              {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4 text-slate-400" />}
              <span>{copied ? 'تم النسخ بنجاح' : 'نسخ الملف'}</span>
            </button>
            <button
              onClick={handleDownload}
              className="flex items-center gap-1.5 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs sm:text-sm font-semibold shadow transition"
            >
              <Download className="w-4 h-4" />
              <span>تحميل الملف</span>
            </button>
          </div>
        </div>

        {/* File Tabs Switcher */}
        <div className="flex space-x-2 space-x-reverse overflow-x-auto mt-6 pt-4 border-t border-slate-800 scrollbar-none">
          {SOURCE_CODE_FILES.map((file) => (
            <button
              key={file.id}
              onClick={() => setSelectedFileId(file.id)}
              className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-mono font-semibold whitespace-nowrap transition ${
                selectedFileId === file.id
                  ? 'bg-indigo-600 text-white shadow'
                  : 'bg-slate-950/80 text-slate-400 hover:text-white border border-slate-800 hover:bg-slate-800'
              }`}
            >
              <FileCode className="w-4 h-4 text-indigo-300" />
              <span>{file.filename}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Active File Meta and Code Preview */}
      <div className="bg-slate-950 rounded-2xl border border-slate-800 overflow-hidden shadow-2xl">
        {/* File Header Bar */}
        <div className="bg-slate-900/90 px-5 py-3 border-b border-slate-800 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div>
            <span className="font-mono text-indigo-400 font-bold block">{activeFile.path}</span>
            <span className="text-slate-400 text-[11px] mt-0.5 block">{activeFile.descriptionAr}</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 font-mono text-[11px] uppercase border border-slate-700">
              {activeFile.language}
            </span>
          </div>
        </div>

        {/* Code Content Box */}
        <div className="p-4 sm:p-6 overflow-x-auto text-xs sm:text-sm font-mono text-slate-200 leading-relaxed max-h-[600px] overflow-y-auto scrollbar-thin">
          <pre className="text-left" dir="ltr">
            <code>{activeFile.code}</code>
          </pre>
        </div>
      </div>
    </div>
  );
};
