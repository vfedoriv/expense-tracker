interface LoadingSpinnerProps {
  size?: 'sm' | 'md' | 'lg';
  message?: string;
}

const sizeClasses = {
  sm: 'h-4 w-4',
  md: 'h-8 w-8',
  lg: 'h-12 w-12',
} as const;

export function LoadingSpinner({ size = 'md', message }: LoadingSpinnerProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-8">
      <div
        className={`${sizeClasses[size]} animate-spin rounded-full border-2 border-gray-200 border-t-blue-600`}
        role="status"
        aria-label="Loading"
      />
      {message && (
        <p className="text-sm text-gray-500">{message}</p>
      )}
    </div>
  );
}
