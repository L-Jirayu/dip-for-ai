function image_moment(path)
% image_moment.m  (T=128, no Otsu, borderless, no NaN, axis clipped)
% - อ่านภาพ -> gray (0..255) -> binary 0/1 ด้วย T=128
% - คำนวณ Raw/Central/NormalizedCentral + Hu(7)
% - แสดง Hu แบบ sign-preserving log
% - วาด centroid + principal axis (ถูก "คลิป" ให้อยู่ในกรอบภาพ)
% - เซฟรูป 3 ใบ (ไร้ขอบ) + รายงาน .txt

    if nargin < 1 || isempty(path)
        path = 'threshold_result/threshold_single/threshold_1.png';
    end

    % ---------- Read ----------
    img = imread(path);
    assert(~isempty(img), 'Cannot read image: %s', path);

    % ---------- Gray (0..255 double) ----------
    if ndims(img) == 3
        r = double(img(:,:,1)); g = double(img(:,:,2)); b = double(img(:,:,3));
        gray = 0.299*r + 0.587*g + 0.114*b;
    else
        gray = double(img);
    end

    % ---------- Binary 0/1 (T=128) ----------
    f = zeros(size(gray));
    f(gray >= 128.0) = 1.0;

    % ---------- Moments ----------
    M00 = rawMoment(f,0,0);
    if M00 == 0
        xbar = 0; ybar = 0; mu11 = 0; mu20 = 0; mu02 = 0; hu = zeros(7,1);
    else
        S10 = rawMoment(f,1,0);
        S01 = rawMoment(f,0,1);
        xbar = S10/M00;  ybar = S01/M00;
        mu11 = centralMoment(f,1,1);
        mu20 = centralMoment(f,2,0);
        mu02 = centralMoment(f,0,2);
        hu   = huMoments(f);
    end
    hu(~isfinite(hu)) = 0;  % กัน NaN/Inf

    M10_disp = xbar; M01_disp = ybar;

    % ---------- Console ----------
    fprintf('Raw Moments\n');
    fprintf('M00 = %.2f\n', M00);
    fprintf('M10 = %.2f,  M01 = %.2f\n', M10_disp, M01_disp);
    fprintf('Centroid (x̄, ȳ) = (%.2f, %.2f)\n\n', xbar, ybar);

    fprintf('Central Moments\nmu00 = %.2f\nmu11 = %.2f\nmu20 = %.2f\nmu02 = %.2f\n\n', ...
            M00, mu11, mu20, mu02);

    fprintf('Hu Moments (raw)\n');
    for i=1:7, fprintf('phi%d = %.6e\n', i, hu(i)); end

    epsv  = 1e-12;
    huLog = -sign(hu) .* log10(abs(hu) + epsv);

    % ---------- Folders ----------
    imgDir = fullfile('image_moment','picture_output');
    txtDir = fullfile('image_moment','txt_output');
    if ~exist(imgDir,'dir'), mkdir(imgDir); end
    if ~exist(txtDir,'dir'), mkdir(txtDir); end

    % ---------- Report ----------
    txtPath = fullfile(txtDir,'moment_report.txt');
    fid = fopen(txtPath,'w'); assert(fid~=-1,'Cannot open %s',txtPath);
    cleaner = onCleanup(@() fclose(fid));
    fprintf(fid,'Raw Moments\nM00 = %.2f\nM10 = %.2f,  M01 = %.2f\nCentroid (x̄, ȳ) = (%.2f, %.2f)\n\n', ...
            M00, M10_disp, M01_disp, xbar, ybar);
    fprintf(fid,'Central Moments\nmu00 = %.2f\nmu11 = %.2f\nmu20 = %.2f\nmu02 = %.2f\n\n', ...
            M00, mu11, mu20, mu02);
    fprintf(fid,'Hu Moments (raw)\n'); for i=1:7, fprintf(fid,'phi%d = %.6e\n', i, hu(i)); end
    fprintf(fid,'\nHu Moments (sign-log)\n'); for i=1:7, fprintf(fid,'phi%d_log = %.6f\n', i, huLog(i)); end
    clear cleaner;

    % ---------- Draw & Export (no border) ----------
    [H,W,~] = size(img);
    [cx, cy] = imageCoordsFromZeroOriginCenter(xbar, ybar);

    % 1) raw_moment.png
    rawFig = figure('Visible','off','Color','k'); ax = axes('Parent',rawFig);
    imshow(img,'Parent',ax); hold(ax,'on');
    drawCentroidCross(ax, cx, cy);
    drawBottomLabel(ax, {
        sprintf('Raw: M00=%.0f', M00)
        sprintf('M10=%.2f, M01=%.2f', M10_disp, M01_disp)
        sprintf('Centroid (%.2f, %.2f)', xbar, ybar)
    });
    borderlessExport(rawFig, ax, W, H, fullfile(imgDir,'raw_moment.png'));

    % 2) central_moment.png
    cenFig = figure('Visible','off','Color','k'); ax = axes('Parent',cenFig);
    imshow(img,'Parent',ax); hold(ax,'on');
    drawCentroidCross(ax, cx, cy);
    drawBottomLabel(ax, {
        sprintf('Central: mu11=%.2f', mu11)
        sprintf('mu20=%.2f, mu02=%.2f', mu20, mu02)
    });
    borderlessExport(cenFig, ax, W, H, fullfile(imgDir,'central_moment.png'));

    % 3) hu_moment.png  (principal axis clipped to image bounds)
    huFig = figure('Visible','off','Color','k'); ax = axes('Parent',huFig);
    imshow(img,'Parent',ax); hold(ax,'on');
    drawCentroidCross(ax, cx, cy);

    theta = 0.5 * atan2(2.0*mu11, (mu20 - mu02));
    [x1,y1,x2,y2] = clippedPrincipalAxis(cx,cy,theta,W,H);   % ★ คลิปเส้นให้จบในภาพ
    line(ax,[x1 x2],[y1 y2],'LineWidth',2,'Color',[1 0.78 0],'Clipping','on');

    drawBottomLabel(ax, {
        sprintf('Hu (sign-log): \\phi1=%.2f  \\phi2=%.2f  \\phi3=%.2f  \\phi4=%.2f', huLog(1),huLog(2),huLog(3),huLog(4))
        sprintf('               \\phi5=%.2f  \\phi6=%.2f  \\phi7=%.2f', huLog(5),huLog(6),huLog(7))
    });
    borderlessExport(huFig, ax, W, H, fullfile(imgDir,'hu_moment.png'));

    close([rawFig, cenFig, huFig]);

    % ---------- Summary ----------
    fprintf('\nSaved:\n  %s\n  %s\n  %s\n  %s\n', ...
        fullfile(imgDir,'raw_moment.png'), ...
        fullfile(imgDir,'central_moment.png'), ...
        fullfile(imgDir,'hu_moment.png'), ...
        fullfile(txtDir,'moment_report.txt'));
end

% ========================= Helpers =========================

function borderlessExport(figH, axH, W, H, outPath)
    set(figH,'Units','pixels','Position',[100 100 W H], ...
             'PaperPositionMode','auto','MenuBar','none','ToolBar','none','Color','black');
    axis(axH,'image'); axis(axH,'off');
    set(axH,'Units','normalized','Position',[0 0 1 1],'LooseInset',[0 0 0 0]);
    exportgraphics(figH, outPath, 'ContentType','image', 'BackgroundColor','black', 'Resolution', 200);
end

function M = rawMoment(f, p, q)
    [h,w] = size(f);
    [J,I] = meshgrid(0:w-1, 0:h-1);
    M = sum( (I.^p) .* (J.^q) .* f, 'all' );
end

function mu = centralMoment(f, p, q)
    M00 = rawMoment(f,0,0);
    if M00==0, mu=0; return; end
    M10 = rawMoment(f,1,0);
    M01 = rawMoment(f,0,1);
    xbar = M10/M00; ybar = M01/M00;
    [h,w] = size(f);
    [J,I] = meshgrid(0:w-1, 0:h-1);
    mu = sum( ((I - ybar).^p) .* ((J - xbar).^q) .* f, 'all' );
end

function val = mu00(f), val = rawMoment(f,0,0); end

function eta = normalizedCentralMoment(f, p, q)
    mu00v = mu00(f);
    if mu00v==0, eta=0; return; end
    muPQ  = centralMoment(f,p,q);
    gamma = 1.0 + 0.5*(p+q);
    eta   = muPQ / (mu00v^gamma);
end

function phi = huMoments(f)
    n20 = normalizedCentralMoment(f,2,0);
    n02 = normalizedCentralMoment(f,0,2);
    n11 = normalizedCentralMoment(f,1,1);
    n30 = normalizedCentralMoment(f,3,0);
    n03 = normalizedCentralMoment(f,0,3);
    n21 = normalizedCentralMoment(f,2,1);
    n12 = normalizedCentralMoment(f,1,2);
    phi = zeros(7,1);
    phi(1) = n20 + n02;
    phi(2) = (n20 - n02)^2 + 4*n11^2;
    phi(3) = (n30 - 3*n12)^2 + (3*n21 - n03)^2;
    phi(4) = (n30 + n12)^2 + (n21 + n03)^2;
    phi(5) = (n30 - 3*n12)*(n30 + n12)*((n30 + n12)^2 - 3*(n21 + n03)^2) + ...
             (3*n21 - n03)*(n21 + n03)*(3*(n30 + n12)^2 - (n21 + n03)^2);
    phi(6) = (n20 - n02)*((n30 + n12)^2 - (n21 + n03)^2) + 4*n11*(n30 + n12)*(n21 + n03);
    phi(7) = (3*n21 - n03)*(n30 + n12)*((n30 + n12)^2 - 3*(n21 + n03)^2) - ...
             (n30 - 3*n12)*(n21 + n03)*(3*(n30 + n12)^2 - (n21 + n03)^2);
end

function [cx, cy] = imageCoordsFromZeroOriginCenter(xbar, ybar)
    cx = xbar + 1;  cy = ybar + 1;  % 1-based for plotting
end

function drawCentroidCross(ax, cx, cy)
    len = 8;
    plot(ax, [cx-len, cx+len],[cy, cy],'g-','LineWidth',2);
    plot(ax, [cx, cx],[cy-len, cy+len],'g-','LineWidth',2);
    plot(ax, cx, cy, 'go', 'MarkerSize',5, 'LineWidth',1);
end

function drawBottomLabel(ax, lines)
    xl = xlim(ax); yl = ylim(ax);
    text(ax, xl(2)-10, yl(2)-10, lines, ...
        'HorizontalAlignment','right','VerticalAlignment','bottom', ...
        'FontName','SansSerif','FontWeight','bold','FontSize',12, ...
        'Color','w','BackgroundColor',[0 0 0 0.6],'Margin',6);
end

% ---- Clip infinite principal axis to the image rectangle ----
function [x1,y1,x2,y2] = clippedPrincipalAxis(cx,cy,theta,W,H)
    dx = cos(theta); dy = sin(theta);
    % Candidate intersections with rectangle x=1|W, y=1|H
    t = [];
    % x = 1
    if abs(dx) > eps
        ty = cy + (1 - cx) * dy/dx;
        if ty >= 1 && ty <= H, t(end+1) = (1 - cx)/dx; end
        ty = cy + (W - cx) * dy/dx;
        if ty >= 1 && ty <= H, t(end+1) = (W - cx)/dx; end
    end
    % y = 1 or H
    if abs(dy) > eps
        tx = cx + (1 - cy) * dx/dy;
        if tx >= 1 && tx <= W, t(end+1) = (1 - cy)/dy; end
        tx = cx + (H - cy) * dx/dy;
        if tx >= 1 && tx <= W, t(end+1) = (H - cy)/dy; end
    end
    if numel(t) < 2
        % fallback: very short segment at centroid
        x1 = cx; y1 = cy; x2 = cx; y2 = cy; return;
    end
    t = sort(t);
    p1 = [cx + t(1)*dx, cy + t(1)*dy];
    p2 = [cx + t(end)*dx, cy + t(end)*dy];
    % clamp (safety)
    x1 = min(max(p1(1),1),W); y1 = min(max(p1(2),1),H);
    x2 = min(max(p2(1),1),W); y2 = min(max(p2(2),1),H);
end
