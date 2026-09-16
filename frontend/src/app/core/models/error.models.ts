export interface ErrorResponse {
  timestamp: string;
  status: number;
  message: string;
  fieldErrors?: Record<string, string> | null;
}
