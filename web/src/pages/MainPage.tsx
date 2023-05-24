import React from 'react';

import styles from './MainPage.module.css';

import MenuIcon from '@rsuite/icons/Menu';
import { useFetch } from 'usehooks-ts';
import { PageResponse, PathsResponse } from '../types';
import { Sidebar } from '../components/Sidebar';
import { DocumentView } from '../components/ast/DocumentView';
import { Button } from '../design/Button';
import { useLocation, useNavigate } from 'react-router-dom';

export function MainPage() {
  const navigate = useNavigate();
  const location = useLocation();

  const [windowWidth, setWindowWidth] = React.useState(window.innerWidth);
  const isOnSmallScreen = determineIsOnSmallScreen(windowWidth);
  const [open, setOpen] = React.useState(!isOnSmallScreen);

  window.addEventListener('resize', () => {
    const newWidth = window.innerWidth;
    setWindowWidth(newWidth);
  });

  const pathname = location.pathname;
  const trimmedPathname = pathname.startsWith('/') ? pathname.substring(1) : pathname;

  const { data: activePathData } = useFetch<PageResponse>(
    `/api/page?path=${encodeURIComponent(trimmedPathname)}`);
  const { data: pathsData } = useFetch<PathsResponse>('/api/paths');

  React.useEffect(() => {
    const first = pathsData?.Paths?.[0];
    if (location.pathname === "/" && first !== undefined) {
      navigate(first.Path);
    }
  }, [pathsData]);

  const sidebar = (
    <div className={styles.sidebar}>
      <Sidebar
        selectedPath={trimmedPathname}
        allPaths={pathsData?.Paths ?? null}
        onSelect={(pathItem) => {
          if (pathItem.endsWith('.math') && isOnSmallScreen) {
            setOpen(false);
          }
        }}/>
    </div>
  );

  const mainContent = (
    <div className={styles.mainContent}>
      {activePathData?.Document && (
        <div className={styles.page}>
          <DocumentView node={activePathData?.Document} isOnSmallScreen={isOnSmallScreen} />
        </div>
      )}
    </div>
  );


  const leftStyle = {
    display: open ? 'block' : 'none',
  };

  return (
    <div className={styles.outerWrapper}>
      <header className={styles.header}>
        <Button flat
                onClick={() => {
          setOpen(open => !open);
        }}>
          <MenuIcon style={{
            color: 'black',
            marginLeft: 10,
            marginRight: 0,
            marginTop: 0,
            marginBottom: 0,
            padding: 0,
          }} />
        </Button>
      </header>
      <div className={styles.contentWrapper}>
        <main className={styles.content}>
          <nav className={styles.left} style={leftStyle}>
            {sidebar}
          </nav>
          <div className={styles.center}>
            {mainContent}
          </div>
        </main>
      </div>
      <footer className={styles.footer}>
      </footer>
    </div>
  );
}

function determineIsOnSmallScreen(windowWidth: number) {
  return windowWidth <= 450;
}
