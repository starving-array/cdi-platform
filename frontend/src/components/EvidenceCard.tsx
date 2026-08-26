import React from 'react';
import { EvidenceRecord } from '../types';
import { EvidenceSourceBadge } from './EvidenceSourceBadge';
import { Calendar, Cpu, Tag } from 'lucide-react';

export interface EvidenceCardProps {
  record: EvidenceRecord;
}

export const EvidenceCard: React.FC<EvidenceCardProps> = ({ record }) => {
  const formattedDate = new Date(record.capturedAt).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <div className="p-5 rounded-xl border border-slate-800 bg-slate-900/40 hover:bg-slate-900/60 transition-colors space-y-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-2">
          <EvidenceSourceBadge source={record.source} />
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-mono font-medium bg-slate-800 text-slate-300 border border-slate-700">
            <Tag className="w-3 h-3 text-slate-500" aria-hidden="true" />
            <span>Origin: {record.origin}</span>
          </span>
        </div>

        <div className="flex items-center gap-1.5 text-xs text-slate-500">
          <Calendar className="w-3.5 h-3.5" aria-hidden="true" />
          <span>{formattedDate}</span>
        </div>
      </div>

      <div>
        <h3 className="text-base font-semibold text-slate-100">{record.title}</h3>
        {record.content && (
          <p className="text-xs text-slate-300/90 leading-relaxed mt-2 p-3 rounded-lg bg-slate-950/60 border border-slate-800/80 font-mono whitespace-pre-wrap">
            {record.content}
          </p>
        )}
      </div>

      <div className="pt-2 border-t border-slate-800/60 flex items-center justify-between text-xs text-slate-500 font-mono">
        <div className="flex items-center gap-1.5">
          <Cpu className="w-3.5 h-3.5 text-slate-500" aria-hidden="true" />
          <span>Analysis Run: {record.analysisRunId}</span>
        </div>
        <span className="text-[11px] text-slate-600 truncate max-w-[200px]" title={record.id}>
          ID: {record.id.substring(0, 8)}...
        </span>
      </div>
    </div>
  );
};