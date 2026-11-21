import { useQuery } from '@tanstack/react-query';
import { useT } from '../../shared/i18n/LanguageProvider';
import { ErrorState, Skeleton } from '../../shared/ui/States';
import { tasksText } from './i18n';
import { githubTaskLinksQuery } from './queries';

export default function TaskGithubLinks({
  projectId,
  taskId,
}: {
  projectId: number;
  taskId: number;
}) {
  const t = useT(tasksText);
  const links = useQuery(githubTaskLinksQuery(projectId, taskId));
  if (links.isPending) return <Skeleton />;
  if (links.isError)
    return (
      <ErrorState
        detail={t('githubLinksError')}
        onRetry={() => void links.refetch()}
      />
    );
  if (!links.data.length) return <p>{t('githubLinksEmpty')}</p>;
  return (
    <ul>
      {links.data.map((link) => (
        <li key={`${link.repository}/${link.kind}/${link.key}`}>
          <a href={link.url} target="_blank" rel="noopener noreferrer">
            {link.kind === 'PULL_REQUEST'
              ? `#${link.key}`
              : link.key.slice(0, 7)}{' '}
            {link.title}
          </a>
          <small>
            {link.repository}
            {link.state &&
              ` · ${t(link.state === 'MERGED' ? 'githubMerged' : link.state === 'CLOSED' ? 'githubClosed' : 'githubOpen')}`}
          </small>
        </li>
      ))}
    </ul>
  );
}
