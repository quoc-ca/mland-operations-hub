from pathlib import Path
import re
import html

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/05-system-fuctionalities/01-screen-inventory/02-screen-list.md"
OUT = Path(r"C:\Users\ADMIN\.codex\visualizations\2026\10\09\01a11f10-1834-77d1-9e5c-3dca7419d8bd\sep490-figma-import")
W, H = 1440, 900
GOLD, INK, SLATE, LINE, SUB, MUTED = "#C59A27", "#1A1A1A", "#2D3139", "#E9ECEF", "#F8F9FA", "#F1F3F5"
NAVY = "#26374A"

def esc(s): return html.escape(str(s), quote=True)
def text(x,y,s,size=14,fill=INK,weight=400,anchor="start",letter=0):
    return f'<text x="{x}" y="{y}" fill="{fill}" font-family="Segoe UI,Arial,sans-serif" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}" letter-spacing="{letter}">{esc(s)}</text>'
def rect(x,y,w,h,fill="#fff",stroke=LINE,rx=8,sw=1):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>'

rows=[]
for line in REPORT.read_text(encoding="utf-8").splitlines():
    if line.startswith("| ") and not re.match(r"\|\s*#\s*\|",line) and not re.match(r"\|\s*-",line):
        cells=[c.strip() for c in line.strip("|").split("|")]
        if len(cells)>=4 and cells[0].isdigit(): rows.append((int(cells[0]),*cells[1:4]))
assert len(rows)==78, f"Expected 78 screens from Report 3, got {len(rows)}"

def slug(s): return re.sub(r"[^a-z0-9]+","-",s.lower()).strip("-")
def role_for(i,feature):
    if i in (1,3,4,5,6,7,10,12): return "Guest"
    if i in (2,8,9) or 13<=i<=13 or 17<=i<=46: return "Member"
    if 14<=i<=14 or 47<=i<=60: return "Staff"
    if 15<=i<=15 or 61<=i<=71: return "Manager"
    if 16<=i<=16 or 72<=i<=78: return "Admin"
    if i in (11,): return "Guest / Member"
    return "Member"
def lines_wrap(s, n=68):
    words=s.split(); out=[]; cur=""
    for word in words:
        if cur and len(cur)+1+len(word)>n: out.append(cur); cur=word
        else: cur=(cur+" "+word).strip()
    if cur: out.append(cur)
    return out
def panel(title,x,y,w,h,content):
    z=[rect(x,y,w,h),text(x+20,y+30,title,15,INK,700)]
    z.append(f'<line x1="{x+16}" y1="{y+44}" x2="{x+w-16}" y2="{y+44}" stroke="{LINE}"/>')
    return "".join(z+content)
def tag(x,y,label,color=GOLD,bg="#FFF7DF"):
    width=max(76,len(label)*7.2+22)
    return rect(x,y,width,26,bg,bg,13)+text(x+width/2,y+17,label,11,color,700,"middle")
def field(x,y,label,value="",w=480):
    return text(x,y,label.upper(),10,SLATE,700,letter=1)+rect(x,y+9,w,42,"#FFFFFF",LINE,5)+text(x+12,y+36,value or "Nhập thông tin…",13,SLATE if not value else INK)
def button(x,y,label,primary=False,w=164):
    fill=INK if primary else "#FFFFFF"; fg="#FFFFFF" if primary else INK
    return rect(x,y,w,42,fill,fill if primary else INK,5)+text(x+w/2,y+27,label,11,fg,700,"middle",.5)
def stat(x,y,label,value,foot):
    return rect(x,y,222,105)+text(x+18,y+27,label,11,SLATE,600)+text(x+18,y+62,value,25,INK,700)+text(x+18,y+85,foot,10,SLATE)
def table(x,y,w,headers,items):
    z=[rect(x,y,w,39,MUTED,MUTED,5)]
    colw=w/len(headers)
    for j,h in enumerate(headers): z.append(text(x+16+j*colw,y+25,h,10,SLATE,700))
    for ri,row in enumerate(items):
        yy=y+47+ri*49; z += [rect(x,yy,w,42,"#fff",LINE,4)]
        for j,val in enumerate(row): z.append(text(x+16+j*colw,yy+26,str(val),11,INK if j==0 else SLATE,600 if j==0 else 400))
    return "".join(z)

def render_screen(row):
    i,name,feature,desc=row; name=name.strip(); feature=feature.strip(); desc=desc.strip(); role=role_for(i,feature)
    title=esc(name); group=f"{i:02d} {slug(name)}"
    z=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',f'<title>{i:02d} {title}</title>',rect(0,0,W,H,SUB,SUB,0),
       rect(0,0,W,72,"#FFFFFF",LINE,0),text(48,47,"M",30,GOLD,800),text(75,44,"mland",23,INK,700),
       text(340,43,"WORKSHOPS",10,SLATE,700,letter=1.3),text(455,43,"READY RINGS",10,SLATE,700,letter=1.3),text(580,43,"CUSTOM",10,SLATE,700,letter=1.3),
       text(1072,42,"Hỗ trợ",12,SLATE),text(1150,42,"VI  ⌄",12,SLATE),rect(1230,17,158,38,INK,INK,5),text(1309,42,"Tài khoản",11,"#FFFFFF",700,"middle"),
       rect(0,72,242,828,"#FFFFFF",LINE,0), text(27,116,"MLAND • OPERATIONS",10,GOLD,700,letter=1.4),text(27,158,"Không gian làm việc",16,INK,700),
       rect(16,181,210,40,"#FFFFFF","#FFFFFF",5),text(37,207,"⌂",18,GOLD,700),text(64,206,"Tổng quan",12,SLATE),
       rect(16,229,210,40,"#FFFFFF","#FFFFFF",5),text(37,255,"◈",18,GOLD,700),text(64,254,"Workshop & thiết kế",12,SLATE),
       rect(16,277,210,40,"#FFFFFF","#FFFFFF",5),text(37,303,"▤",18,GOLD,700),text(64,302,"Đơn hàng",12,SLATE),
       rect(16,325,210,40,"#FFFFFF","#FFFFFF",5),text(37,351,"◇",18,GOLD,700),text(64,350,"Sản phẩm",12,SLATE),
       rect(16,373,210,40,"#FFFFFF","#FFFFFF",5),text(37,399,"◉",18,GOLD,700),text(64,398,"Khách hàng",12,SLATE),
       rect(16,421,210,40,"#FFFFFF","#FFFFFF",5),text(37,447,"⚙",18,GOLD,700),text(64,446,"Cài đặt",12,SLATE),
       text(27,861,f"{role.upper()} WORKSPACE",10,SLATE,700,letter=1),
       text(278,108,f"Report 3  /  {esc(feature)}",11,SLATE),text(278,157,name,30,INK,700),text(278,184,desc,12,SLATE),
       tag(1196,128,role.upper()),text(1374,858,f"SCREEN {i:02d} / 78",10,SLATE,700,"end",1),
       rect(278,202,1098,1,LINE,LINE,0)]
    # Common content layouts guided by screen task, not generic wireframes.
    f=feature.lower(); n=name.lower();
    if i==1:
        z += [rect(278,224,1098,364,NAVY,NAVY,8),text(320,286,"TRẢI NGHIỆM CHẾ TÁC CỦA RIÊNG BẠN",11,"#E8CF93",700,letter=1.3),text(320,354,"Một chiếc nhẫn,",48,"#FFFFFF",700),text(320,409,"một câu chuyện riêng.",48,"#FFFFFF",700),text(320,453,"Khám phá ready rings, workshop và đánh giá từ khách hàng.",14,"#E9ECEF"),button(320,497,"ĐẶT WORKSHOP",True,178),button(512,497,"KHÁM PHÁ NHẪN",False,182),rect(1010,249,322,310,"#D8CFBE","#D8CFBE",5),text(1171,411,"RING / WORKSHOP IMAGE",11,SLATE,700,"middle",1),text(320,646,"Khám phá hành trình",25,INK,700),text(320,680,"Chọn lối bắt đầu phù hợp với bạn",13,SLATE)]+[rect(278+k*269,711,248,129,"#FFFFFF",LINE,6)+tag(296+k*269,727,f"0{k+1}")+text(296+k*269,782,label,17,INK,700)+text(296+k*269,809,sub,11,SLATE) for k,(label,sub) in enumerate([("Ready rings","Nhẫn chế tác sẵn"),("Workshop","Đặt buổi trải nghiệm"),("Custom","Yêu cầu chế tác riêng"),("Reviews","Chia sẻ từ khách hàng")])]
    elif i in (2,3,4,5,6,7,8,9):
        z += [rect(278,224,662,610,"#FFFFFF",LINE,8),text(322,275,"MLAND • YOUR ACCOUNT",10,GOLD,700,letter=1.2),text(322,323,name,28,INK,700),text(322,352,desc,12,SLATE)]
        y=410
        if i in (2,3,5,6,8,9):
            z += [field(322,y,"Email","name@example.com",574)]; y+=75
        if i in (2,3,6,9):
            z += [field(322,y,"Mật khẩu","••••••••",574)]; y+=76
        if i==3: z += [field(322,y,"Xác nhận mật khẩu","••••••••",574)]; y+=76
        if i==8: z += [field(322,y,"Họ và tên","Nguyễn Minh An",574),field(322,y+75,"Số điện thoại","+84 …",574)]; y+=155
        if i==4: z += [rect(322,y,574,105,"#F8F9FA",LINE,5),text(350,y+40,"Kiểm tra hộp thư của bạn",17,INK,700),text(350,y+70,"Liên kết xác minh có thời hạn; gửi lại nếu cần.",12,SLATE)]; y+=125
        if i==7: z += [rect(322,y,574,100,"#F8F9FA",LINE,5),text(350,y+37,"Đang chuyển tới nhà cung cấp",15,INK,700),text(350,y+65,"Kết quả xác thực sẽ quay về MLand.",12,SLATE)]; y+=120
        z += [button(322,y,"TIẾP TỤC",True,180),text(520,y+27,"Google  •  Email  •  Hỗ trợ",11,SLATE),rect(974,224,402,610,"#FFFFFF",LINE,8),text(1010,274,"Quyền riêng tư & phục hồi",18,INK,700)]
        for k,s in enumerate(["Không tiết lộ email có tồn tại hay không.","Provider-managed password đổi tại nhà cung cấp.","Giữ nguyên đích quay lại sau khi đăng nhập.","Nhãn lỗi luôn kèm cách khôi phục."]): z += [text(1010,328+k*64,"✓",14,GOLD,700),text(1037,328+k*64,s,12,SLATE)]
    elif i in (10,11,12):
        z += [field(278,228,"Tìm kiếm","Tìm theo tên, chất liệu…",440),tag(752,247,"Tất cả"),tag(844,247,"Nhẫn",SLATE,MUTED),tag(910,247,"Workshop",SLATE,MUTED),tag(1013,247,"Đánh giá",SLATE,MUTED)]
        for k in range(3):
            x=278+k*365; z += [rect(x,300,337,238,"#E5E0D5",LINE,6),text(x+168,432,["RING 01","RING 02","RING 03"][k],12,SLATE,700,"middle",1),text(x,570,["Nhẫn Vân Mây","Nhẫn Thạch Anh","Nhẫn Lá Mảnh"][k],19,INK,700),text(x,598,"Bạc 925  ·  Còn hàng",11,SLATE),text(x,636,["2.490.000 ₫","3.250.000 ₫","1.890.000 ₫"][k],15,INK,700),button(x,670,"XEM CHI TIẾT",k==0,160)]
        z += [rect(278,762,1098,53,"#FFF7DF","#FFF7DF",5),text(300,795,"Danh sách và giá hiển thị chỉ là dữ liệu minh họa.",12,"#725719",600)]
    elif i in (17,18,24,25,33,34,35):
        z += [stat(278,228,"GIÁ TRỊ ĐƠN HÀNG","2.490.000 ₫","Dữ liệu minh họa"),stat(516,228,"TRẠNG THÁI","Đang giữ chỗ","Đồng bộ hệ thống"),stat(754,228,"THANH TOÁN","Chưa xác nhận","Cổng thanh toán"),
              panel("Sản phẩm / giao dịch",278,354,690,417,[table(298,414,650,["MẶT HÀNG","PHÂN LOẠI","SL","THÀNH TIỀN"],[["Nhẫn Vân Mây","Bạc 925","1","2.490.000 ₫"],["Tạm tính","—","—","2.490.000 ₫"]])]),
              panel("Tóm tắt & bước tiếp",990,354,386,417,[text(1012,433,"Tổng thanh toán",12,SLATE),text(1012,473,"2.490.000 ₫",23,INK,700),text(1012,518,"Toàn bộ giỏ được kiểm tra lại khi gửi.",11,SLATE),text(1012,556,"Không thanh toán từng phần.",11,SLATE),button(1012,590,"TIẾP TỤC",True,190),rect(1012,651,338,52,"#FFF7DF","#FFF7DF",5),text(1028,682,"Return URL không xác nhận đã thanh toán.",10,"#725719",600)])]
    elif 19<=i<=23 or i in (26,27):
        z += [rect(278,225,1098,56,"#FFFFFF",LINE,5),text(300,261,"01 GÓI  →  02 ĐỊA ĐIỂM  →  03 THIẾT KẾ  →  04 XÁC NHẬN",11,SLATE,700,letter=.4),
              panel("Chi tiết đặt workshop",278,306,680,478,[text(302,383,"Signature Workshop",21,INK,700),text(302,417,"Mô tả gói / ngày / ca / số người",12,SLATE),tag(302,440,"CÒN CHỖ"),text(302,520,"Chọn chi nhánh",11,SLATE,700),field(302,538,"Chi nhánh / ca","Chọn ngày và khung giờ",606),field(302,616,"Thông tin người tham gia","Họ tên và liên hệ",606),button(302,710,"TIẾP THEO",True,162)]),
              panel("Điều khoản giữ chỗ",982,306,394,478,[text(1006,383,"Tiền cọc 50%",16,INK,700),text(1006,414,"Giữ sức chứa trong 15 phút.",12,SLATE),rect(1006,440,346,94,"#FFF7DF","#FFF7DF",5),text(1024,473,"Chưa xác nhận",15,"#725719",700),text(1024,499,"Chỉ IPN / QueryDR hợp lệ mới chốt lịch.",10,"#725719"),text(1006,574,"Thiết kế chỉ mở sau khi chọn gói.",11,SLATE),text(1006,611,"Mã QR chỉ hiện sau xác nhận thanh toán.",11,SLATE)])]
    elif 36<=i<=46:
        z += [panel("Khu vực làm việc với thiết kế",278,228,710,505,[rect(300,290,666,282,"#F1F3F5",LINE,6),text(633,438,"PREVIEW • DESIGN CANVAS",13,SLATE,700,"middle",1),text(300,607,"Thiết kế nhẫn",20,INK,700),text(300,635,"Điều chỉnh thuộc tính được hỗ trợ; AI chỉ gợi ý.",12,SLATE),button(300,667,"LƯU BẢN NHÁP",False,174),button(490,667,"GỬI DUYỆT",True,158)]),
              panel("Tùy chọn & kiểm tra",1004,228,372,505,[tag(1028,290,"BƯỚC 3 / 5"),field(1028,342,"Chất liệu","Bạc 925",322),field(1028,416,"Kích thước","Chọn size",322),field(1028,490,"Chi tiết","Chọn cấu hình",322),rect(1028,570,322,76,"#FFF7DF","#FFF7DF",5),text(1044,600,"Giá ước tính • độ khó",11,"#725719",700),text(1044,625,"Chờ duyệt theo cấu hình",11,"#725719"),text(1028,689,"Đồng ý xử lý ảnh (bắt buộc)",11,SLATE)])]
    elif i in (13,14,15,16,61):
        z += [stat(278,228,"HÀNH ĐỘNG CẦN XỬ LÝ","08","Dữ liệu minh họa"),stat(516,228,"WORKSHOP HÔM NAY","12","Theo phạm vi quyền"),stat(754,228,"ĐƠN ĐANG MỞ","24","Cập nhật gần nhất: —"),stat(992,228,"DOANH THU","—","Chỉ Manager / báo cáo trễ sẽ gắn nhãn"),panel("Việc cần chú ý",278,365,700,430,[table(298,422,660,["MỤC","NGƯỜI PHỤ TRÁCH","TRẠNG THÁI"],[["Thiết kế cần duyệt","Staff / Manager","Chờ xử lý"],["Phiên workshop","Chi nhánh","Sắp diễn ra"],["Đề xuất cần xem","Manager","Chờ quyết định"],["Đơn cần bàn giao","Staff","Đang chuẩn bị"]])]),panel("Lối tắt",1000,365,376,430,[text(1024,431,"Đi tới nghiệp vụ",15,INK,700),button(1024,460,"MỞ DANH SÁCH",True,198),text(1024,537,"Phạm vi theo vai trò",12,SLATE,600),text(1024,570,"Không có quyền doanh thu?",11,SLATE),text(1024,594,"Không hiển thị dữ liệu doanh thu.",11,SLATE),text(1024,656,"Dữ liệu trên bảng là minh họa.",10,SLATE)])]
    elif i in (47,48,49,50,66,67):
        z += [button(1194,222,"TẠO MỚI / ĐỀ XUẤT",True,182),panel("Quản lý sản phẩm & đề xuất giá",278,294,1098,500,[table(298,352,1058,["MÃ","SẢN PHẨM / ĐỀ XUẤT","GIÁ ĐANG ÁP DỤNG","TRẠNG THÁI"],[["PR-024","Nhẫn Vân Mây","2.490.000 ₫","Đang bán"],["PR-025","Nhẫn Thạch Anh","3.250.000 ₫","Nháp"],["PP-008","Đề xuất giá workshop","—","Chờ Manager"],["PR-026","Nhẫn Lá Mảnh","1.890.000 ₫","Đang giữ chỗ"]]),rect(298,621,1058,110,"#FFF7DF","#FFF7DF",5),text(320,661,"Giá đề xuất không đổi giá đang áp dụng.",14,"#725719",700),text(320,691,"Manager duyệt riêng; giá retail vẫn cần thao tác áp dụng / xuất bản riêng.",11,"#725719"),button(298,748,"LƯU NHÁP",False,142),button(456,748,"GỬI DUYỆT",True,152)])]
    elif i in (51,52,53,62,63,64,65):
        z += [button(1195,222,"TẠO PHIÊN",True,160),panel("Lịch workshop / danh sách phiên",278,294,690,500,[table(298,352,650,["NGÀY • CA","CHI NHÁNH","CHỖ","TRẠNG THÁI"],[["09:00 • 14/10","Quận 1","8 / 10","Mở"],["14:00 • 14/10","Quận 1","10 / 10","Đầy"],["09:00 • 15/10","Thủ Đức","4 / 8","Mở"],["14:00 • 15/10","Thủ Đức","6 / 8","Mở"]]),button(298,610,"MỞ CHI TIẾT",True,170)]),panel("Thông tin phiên",990,294,386,500,[text(1014,358,"Chỉ thao tác trong phạm vi được cấp.",12,SLATE),field(1014,389,"Sức chứa","10",338),field(1014,463,"Nhân sự","Chọn nhân viên",338),rect(1014,548,338,76,"#FFF7DF","#FFF7DF",5),text(1030,579,"Kiểm tra xung đột lịch",12,"#725719",700),text(1030,603,"Bảo vệ booking / hold hiện hữu.",10,"#725719"),button(1014,665,"LƯU THAY ĐỔI",True,176)])]
    elif i in (54,55,70,71):
        z += [panel("Yêu cầu chờ đánh giá",278,228,730,560,[table(298,284,690,["MÃ BOOKING","THÀNH VIÊN","GÓI","TRẠNG THÁI"],[["BK-1042","M. An","Signature","Cần duyệt"],["BK-1041","T. Bình","Silver Clay","Chờ bổ sung"],["BK-1038","H. Linh","Wax","Đang xem"]]),button(298,504,"MỞ HỒ SƠ",True,150)]),panel("Đánh giá / quyết định",1028,228,348,560,[text(1052,292,"Phạm vi quyết định",11,SLATE,700),text(1052,326,"Theo vai trò người duyệt",14,INK,700),field(1052,355,"Đánh giá / giá cuối","Nhập đề xuất",300),rect(1052,443,300,98,"#FFF7DF","#FFF7DF",5),text(1068,477,"Manager phê duyệt cuối",12,"#725719",700),text(1068,504,"Staff không được chốt / thu tiền.",10,"#725719"),button(1052,568,"TRẢ LẠI",False,130),button(1192,568,"PHÊ DUYỆT",True,158)])]
    elif i in (56,57,58,59,60):
        z += [panel("Đơn trong phạm vi vận hành",278,228,730,560,[table(298,284,690,["MÃ ĐƠN","LOẠI","CHI NHÁNH","TRẠNG THÁI"],[["RT-0241","Retail","Quận 1","Đang chuẩn bị"],["CO-0138","Custom","Thủ Đức","Chờ đề xuất"],["RT-0239","Retail","Quận 1","Sẵn sàng nhận"]]),button(298,504,"MỞ VẬN HÀNH",True,180)]),panel("Cập nhật tiến trình",1028,228,348,560,[text(1052,292,"Ghi nhận hành động hợp lệ",12,SLATE),text(1052,336,"●  Đang chuẩn bị",12,INK,600),text(1052,373,"○  Sẵn sàng / khách nhận",12,SLATE),text(1052,410,"○  Bàn giao GHTK thủ công",12,SLATE),field(1052,451,"Mã vận đơn / ghi chú","Chỉ nhập khi có",300),button(1052,533,"LƯU TIẾN TRÌNH",True,192),rect(1052,601,300,82,"#FFF7DF","#FFF7DF",5),text(1068,633,"Không có paid override thủ công.",11,"#725719",700),text(1068,657,"Final amount cần Manager duyệt.",10,"#725719")])]
    elif i in (68,69):
        z += [button(1194,222,"TẠO KHUYẾN MÃI",True,182),panel("Chương trình khuyến mãi",278,294,690,492,[table(298,352,650,["CHIẾN DỊCH","ƯU ĐÃI","THỜI GIAN","TRẠNG THÁI"],[["Member welcome","5%","01–31/10","Nháp"],["Workshop season","10%","15–30/10","Đang chạy"],["Loyalty week","Điểm x2","01–07/11","Tắt"]])]),panel("Thiết lập & kiểm tra",990,294,386,492,[field(1014,354,"Tên chương trình","Tên khuyến mãi",338),field(1014,429,"Loại / giá trị","Chọn ưu đãi",338),field(1014,504,"Thời hạn","Chọn ngày",338),rect(1014,585,338,52,"#FFF7DF","#FFF7DF",5),text(1030,616,"Kiểm tra tính hợp lệ trước kích hoạt.",10,"#725719",600),button(1014,663,"LƯU KHUYẾN MÃI",True,190)])]
    elif i in (31,32):
        z += [stat(278,228,"ĐIỂM KHẢ DỤNG","1.250","Ví dụ minh họa"),stat(516,228,"HẠNG THÀNH VIÊN","Silver","Quyền lợi hiện tại"),stat(754,228,"GIAO DỊCH","08","Lịch sử của bạn"),panel("Lịch sử điểm",278,354,1098,438,[table(298,414,1058,["NGÀY","MÔ TẢ","THAY ĐỔI","SỐ DƯ"],[["02/10/2026","Workshop hoàn tất","+120","1.250"],["28/09/2026","Đổi voucher","−200","1.130"],["15/09/2026","Mua ready ring","+80","1.330"]])])]
    elif i in (72,73,74,75,76,77,78):
        z += [panel("Quản trị cấu hình",278,228,700,560,[table(298,284,660,["ĐỐI TƯỢNG","PHẠM VI","CẬP NHẬT","TRẠNG THÁI"],[["U-0214","Member","09/10","Hoạt động"],["U-0213","Staff","08/10","Hoạt động"],["INT-003","Payment","07/10","Kết nối"],["CFG-012","Booking","06/10","Hợp lệ"]]),button(298,502,"MỞ CẤU HÌNH",True,176)]),panel("Quyền và an toàn",1000,228,376,560,[field(1024,286,"Tìm / chọn cấu hình","Chọn đối tượng",328),text(1024,380,"Tác vụ cần xác nhận riêng",12,SLATE,700),rect(1024,400,328,83,"#FFF7DF","#FFF7DF",5),text(1040,433,"Xác nhận trước khi đổi quyền",11,"#725719",700),text(1040,457,"Mọi giá trị nhạy cảm được che.",10,"#725719"),button(1024,516,"XÁC NHẬN",True,158),text(1024,601,"Admin không có quyền duyệt",11,SLATE),text(1024,625,"doanh thu hoặc nghiệp vụ.",11,SLATE),text(1024,696,"Chẩn đoán đơn chỉ tối thiểu hóa.",10,SLATE)])]
    else:
        z += [panel("Nội dung màn hình",278,228,700,560,[text(304,301,feature,12,GOLD,700),*[text(304,345+k*29,line,13,SLATE) for k,line in enumerate(lines_wrap(desc,78))],rect(304,450,648,220,"#F8F9FA",LINE,6),text(628,558,"NỘI DUNG / TRẠNG THÁI NGHIỆP VỤ",12,SLATE,700,"middle",1)]),panel("Tác vụ",1000,228,376,560,[text(1024,294,"Thao tác theo quyền truy cập",12,SLATE),field(1024,332,"Thông tin","Xem / cập nhật",328),button(1024,420,"TIẾP TỤC",True,174),rect(1024,498,328,96,"#FFF7DF","#FFF7DF",5),text(1040,532,"Quy tắc nghiệp vụ",12,"#725719",700),text(1040,558,"Thay đổi có kiểm soát theo đặc tả.",10,"#725719")])]
    z += [text(278,859,"MLAND • ARTISANAL ATELIER",10,SLATE,700,letter=1),text(1376,859,"NỘI DUNG / SỐ LIỆU TRÊN FRAME LÀ MINH HỌA",9,SLATE,600,"end",.7),"</svg>"]
    return "".join(z)

def render_mobile(index,title,subtitle):
    w,h=390,844; z=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">',f'<title>MOBILE {index:02d} {esc(title)}</title>',rect(0,0,w,h,SUB,SUB,0),rect(0,0,w,64,"#fff",LINE,0),text(24,43,"M",27,GOLD,800),text(52,42,"mland",21,INK,700),text(346,41,"VI⌄",11,SLATE,600,"middle"),
        text(24,105,"MEMBER ACCESS",10,GOLD,700,letter=1.2),text(24,151,title,27,INK,700),text(24,181,subtitle,12,SLATE),rect(24,211,342,142,"#fff",LINE,8),text(44,250,"Trải nghiệm MLand",17,INK,700),text(44,278,"Workshop, ready rings và lịch sử",12,SLATE),text(44,301,"được kết nối theo tài khoản của bạn.",12,SLATE),
        field(24,385,"Email","name@example.com",342),field(24,460,"Mật khẩu","••••••••",342),button(24,548,"TIẾP TỤC",True,342),rect(24,611,342,48,"#FFFFFF",LINE,5),text(195,641,"Tiếp tục với Google",12,INK,600,"middle"),text(195,691,"Quên mật khẩu?   •   Tạo tài khoản",11,SLATE,500,"middle"),rect(24,737,342,74,"#FFF7DF","#FFF7DF",5),text(42,768,"Chúng tôi không tiết lộ email có tồn tại.",10,"#725719",600),text(42,789,"Nội dung hiển thị là dữ liệu minh họa.",9,"#725719"),"</svg>"]
    return "".join(z)

OUT.mkdir(parents=True,exist_ok=True)
for row in rows:
    i,name,*_=row
    (OUT/f"{i:02d}-{slug(name)}.svg").write_text(render_screen(row),encoding="utf-8")
mobile=[("Member access / ready ring","Đăng nhập để mua ready rings"),("Auth landing","Chọn phương thức đăng nhập"),("Email sign-in","Đăng nhập bằng email"),("Email registration","Tạo tài khoản Member"),("Password recovery","Khôi phục mật khẩu"),("Policy acceptance","Xem và chấp nhận chính sách"),("Unverified Member banner","Xác minh email của bạn"),("Recovery and security","Khôi phục phiên an toàn"),("Session expired / suspended","Phiên đăng nhập cần xử lý"),("JavaScript unavailable","Bạn vẫn có thể nhận hỗ trợ")]
for k,(t,s) in enumerate(mobile,1): (OUT/f"M{k:02d}-{slug(t)}.svg").write_text(render_mobile(k,t,s),encoding="utf-8")

# Printable contact sheet: index plus named thumbnails for a quick visual audit.
cols,tw,th,gap=4,320,200,18; rows_count=(len(rows)+cols-1)//cols; sh=rows_count*(th+gap)+145
parts=[f'<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="{sh}" viewBox="0 0 1440 {sh}">',rect(0,0,1440,sh,SUB,SUB,0),text(48,57,"SEP490 • REPORT 3 SCREEN LIBRARY",26,INK,700),text(48,86,"78 desktop frames + 10 mobile auth variants • SVG vectors for Figma import",13,SLATE),text(48,111,"Aucun payant / confirmation booking only after verified IPN or QueryDR • Member / Staff / Manager / Admin scope retained",11,SLATE)]
for j,(i,n,f,d) in enumerate(rows):
    x=48+(j%cols)*(tw+gap); y=138+(j//cols)*(th+gap)
    parts += [rect(x,y,tw,th,"#fff",LINE,8),rect(x,y,tw,32,INK,INK,8),text(x+14,y+22,f"{i:02d} • {n}",11,"#fff",700),tag(x+12,y+43,role_for(i,f).upper()),text(x+13,y+91,f,10,GOLD,700),*[text(x+13,y+117+q*19,line,9,SLATE) for q,line in enumerate(lines_wrap(d,50)[:3])],text(x+tw-12,y+th-12,"1440 × 900",8,SLATE,600,"end")]
parts += ["</svg>"]
(OUT/"00-report3-contact-sheet.svg").write_text("".join(parts),encoding="utf-8")
(OUT/"README.md").write_text("""# SEP490 Report 3 — Figma-ready screen library\n\nThis folder contains 78 individual 1440×900 desktop SVG frames, matching the numbered screen inventory in Report 3, 10 390×844 mobile auth variants from the approved frontend Figma handoff, and a contact sheet. All screen UI is native SVG vectors and can be imported into Figma, then ungrouped for editing.\n\n## Import\n\n1. Import `00-report3-contact-sheet.svg` as the overview page.\n2. Import the 78 numbered desktop SVGs into a page named `SEP490 Screens`; optionally import the 10 `M01–M10` mobile variants into `Customer Auth • Mobile`.\n3. Each SVG is one frame/artboard. Ungroup imported SVG groups to edit individual vector objects.\n\nMock figures and example records are labeled as illustrative. The frames include Report 3 role boundaries and workflow constraints, including whole-cart revalidation, 50% workshop deposit and 15-minute hold, booking confirmation only from valid IPN/QueryDR, QR only after confirmation, Staff/Manager approval boundaries, manual carrier handoff, and Admin exclusion from business approvals.\n""",encoding="utf-8")
print(f"Created {len(rows)} desktop frames, {len(mobile)} mobile variants and contact sheet in {OUT}")

