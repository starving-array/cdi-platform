export type ActorRole = 'ENGINEER' | 'TENANT_ADMIN' | 'SYSTEM_WORKER';

export interface Actor {
  id: string;
  role: ActorRole;
}

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

export interface ChangeSummary {
  id: string;
  repositoryId: string;
  providerChangeId: string;
  title: string;
  description: string;
  author: string;
  sourceBranch: string;
  targetBranch: string;
  latestCommitSha: string;
  status: 'OPEN' | 'MERGED' | 'CLOSED';
  createdAt: string;
  updatedAt: string;
}

export interface AnalysisRunSummary {
  id: string;
  commitSha: string;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SUPERSEDED';
  createdAt: string;
  completedAt?: string | null;
}

export interface RiskAssessmentSummary {
  id: string;
  overallScore: number;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  assessmentVersion: string;
}

export interface DecisionRecordSummary {
  id: string;
  outcome: 'APPROVE' | 'REVIEW_REQUIRED' | 'BLOCK';
  policyVersion: string;
  overridden: boolean;
}

export interface ChangeDetail {
  change: ChangeSummary;
  analysisRuns: AnalysisRunSummary[];
  latestRisk?: RiskAssessmentSummary | null;
  latestDecision?: DecisionRecordSummary | null;
}

export interface EvidenceRecord {
  id: string;
  analysisRunId: string;
  source: string;
  origin: string;
  title: string;
  content: string;
  capturedAt: string;
}

export interface SearchEvidenceResponse {
  degraded: boolean;
  results: EvidenceRecord[];
}