interface Props {
  platform: 'android' | 'web';
  size?: number;
}

export default function PlatformBadge({ platform, size = 16 }: Props) {
  return (
    <span
      title={platform === 'android' ? 'Android' : 'Browser'}
      style={{ fontSize: size, lineHeight: 1 }}
    >
      {platform === 'android' ? '📱' : '🌐'}
    </span>
  );
}
