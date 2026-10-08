<mxfile host="app.diagrams.net" agent="Codex">
  <diagram name="Domain Model" id="tickets-domain-model">
    <mxGraphModel dx="6840" dy="3170" grid="0" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="0" pageScale="1" pageWidth="4350" pageHeight="4010" background="#FFFFFF" math="0" shadow="0">
      <root>
        <mxCell id="0" />
        <mxCell id="1" parent="0" />
        <mxCell id="class-User" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#DBEAFE;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="User" vertex="1">
          <mxGeometry height="387" width="520" x="1380" y="650" as="geometry" />
        </mxCell>
        <mxCell id="body-class-User" parent="class-User" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- email: String&lt;/div&gt;&lt;div style=&quot;line-height:20px&quot;&gt;- userName: String&lt;br&gt;- fullName: String&lt;br&gt;- phone: String [0..1]&lt;br&gt;- status: UserStatus&lt;br&gt;- emailVerified: Boolean&lt;br&gt;- platformRole: PlatformRole&lt;br&gt;- organizationRoles: Map&amp;lt;Organization, OrganizationRole&amp;gt;&lt;br&gt;&lt;/div&gt;" vertex="1">
          <mxGeometry height="200" width="520" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-User" parent="class-User" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="520" y="247" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-User" parent="class-User" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ updateProfile(fullName: String, phone: String [0..1]): void&lt;br&gt;+ verifyEmail(): void&lt;br&gt;+ assignRole(org: Organization, role: OrganizationRole): void&lt;br&gt;+ revokeRole(org: Organization): void&lt;br&gt;+ isEligibleBuyer(): Boolean&lt;/div&gt;" vertex="1">
          <mxGeometry height="120" width="520" y="267" as="geometry" />
        </mxCell>
        <mxCell id="class-Organization" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#DBEAFE;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="Organization" vertex="1">
          <mxGeometry height="281" width="520" x="1380" y="80" as="geometry" />
        </mxCell>
        <mxCell id="body-class-Organization" parent="class-Organization" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- name: String&lt;br&gt;- contactEmail: String&lt;br&gt;- contactPhone: String [0..1]&lt;br&gt;- description: String [0..1]&lt;br&gt;- requester: User [0..1]&lt;br&gt;- status: OrganizationStatus&lt;br&gt;- rejectionReason: String [0..1]&lt;/div&gt;" vertex="1">
          <mxGeometry height="160" width="520" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-Organization" parent="class-Organization" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="520" y="200" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-Organization" parent="class-Organization" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ submitForApproval(): void&lt;br&gt;+ approve(): void&lt;br&gt;+ reject(reason: String): void&lt;/div&gt;" vertex="1">
          <mxGeometry height="80" width="520" y="201" as="geometry" />
        </mxCell>
        <mxCell id="class-Coupon" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#EDE9FE;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="Coupon" vertex="1">
          <mxGeometry height="341" width="520" x="660" y="80" as="geometry" />
        </mxCell>
        <mxCell id="body-class-Coupon" parent="class-Coupon" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- code: String&lt;br&gt;- discountType: DiscountType&lt;br&gt;- fixedAmount: Money [0..1]&lt;br&gt;- percentage: Decimal [0..1]&lt;br&gt;- validFrom: Instant&lt;br&gt;- validTo: Instant&lt;br&gt;- isActive: Boolean&lt;br&gt;- maxUses: Integer&lt;/div&gt;" vertex="1">
          <mxGeometry height="180" width="520" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-Coupon" parent="class-Coupon" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="520" y="220" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-Coupon" parent="class-Coupon" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ isActiveAt(now: Instant): Boolean&lt;br&gt;+ calculateDiscount(subtotal: Money): Money&lt;br&gt;+ activate(): void&lt;br&gt;+ deactivate(): void&lt;br&gt;+ reviseTerms(terms: CouponTerms, allocatedUses: Integer): void&lt;/div&gt;" vertex="1">
          <mxGeometry height="120" width="520" y="221" as="geometry" />
        </mxCell>
        <mxCell id="class-CommissionRule" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#CFFAFE;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="CommissionRule" vertex="1">
          <mxGeometry height="201" width="520" x="2100" y="80" as="geometry" />
        </mxCell>
        <mxCell id="body-class-CommissionRule" parent="class-CommissionRule" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- ratePercent: Decimal&lt;br&gt;- fixedFee: Money&lt;br&gt;- effectiveFrom: Instant&lt;br&gt;- effectiveTo: Instant&lt;/div&gt;" vertex="1">
          <mxGeometry height="100" width="520" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-CommissionRule" parent="class-CommissionRule" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="520" y="140" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-CommissionRule" parent="class-CommissionRule" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ isEffectiveAt(now: Instant): Boolean&lt;br&gt;+ calculateFee(remainingAmount: Money): Money&lt;/div&gt;" vertex="1">
          <mxGeometry height="60" width="520" y="141" as="geometry" />
        </mxCell>
        <mxCell id="class-Event" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#CCFBF1;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="Event" vertex="1">
          <mxGeometry height="541" width="580" x="2100" y="650" as="geometry" />
        </mxCell>
        <mxCell id="body-class-Event" parent="class-Event" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- title: String&lt;br&gt;- description: String&lt;br&gt;- category: EventCategory&lt;br&gt;- venueName: String&lt;br&gt;- venueAddress: String&lt;br&gt;- coverImageUrl: String [0..1]&lt;br&gt;- saleStart: Instant&lt;br&gt;- saleEnd: Instant&lt;br&gt;- startTime: Instant&lt;br&gt;- endTime: Instant&lt;br&gt;- status: EventStatus&lt;br&gt;- rejectionReason: String [0..1]&lt;br&gt;- commissionRule: CommissionRule [0..1]&lt;/div&gt;" vertex="1">
          <mxGeometry height="280" width="580" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-Event" parent="class-Event" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="580" y="320" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-Event" parent="class-Event" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ updateDetails(details: EventDetails): void&lt;br&gt;+ configureSchedule(schedule: EventSchedule): void&lt;br&gt;+ addZone(zone: Zone): void&lt;br&gt;+ removeZone(zone: Zone): void&lt;br&gt;+ submitForApproval(): void&lt;br&gt;+ publish(rule: CommissionRule, now: Instant): void&lt;br&gt;+ reject(reason: String): void&lt;br&gt;+ cancel(now: Instant): void&lt;br&gt;+ isSaleActive(now: Instant): Boolean&lt;br&gt;+ isCheckInOpen(now: Instant): Boolean&lt;/div&gt;" vertex="1">
          <mxGeometry height="220" width="580" y="321" as="geometry" />
        </mxCell>
        <mxCell id="class-Zone" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#CCFBF1;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="Zone" vertex="1">
          <mxGeometry height="381" width="520" x="2496" y="2230" as="geometry" />
        </mxCell>
        <mxCell id="body-class-Zone" parent="class-Zone" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- name: String&lt;br&gt;- type: ZoneType&lt;br&gt;- price: Money&lt;br&gt;- standingCapacity: Integer [0..1]&lt;br&gt;- standingHeld: Integer [0..1]&lt;br&gt;- standingSold: Integer [0..1]&lt;/div&gt;" vertex="1">
          <mxGeometry height="140" width="520" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-Zone" parent="class-Zone" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="520" y="180" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-Zone" parent="class-Zone" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ changePrice(price: Money): void&lt;br&gt;+ configureSeating(rows: Integer, seatsPerRow: Integer): void&lt;br&gt;+ setStandingCapacity(capacity: Integer): void&lt;br&gt;+ holdStanding(quantity: Integer): void&lt;br&gt;+ releaseHeldStanding(quantity: Integer): void&lt;br&gt;+ sellHeldStanding(quantity: Integer): void&lt;br&gt;+ returnSoldStanding(quantity: Integer): void&lt;br&gt;+ getCapacity(): Integer&lt;br&gt;+ getAvailable(): Integer&lt;/div&gt;" vertex="1">
          <mxGeometry height="200" width="520" y="181" as="geometry" />
        </mxCell>
        <mxCell id="class-Seat" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#CCFBF1;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="Seat" vertex="1">
          <mxGeometry height="221" width="520" x="2496" y="2765" as="geometry" />
        </mxCell>
        <mxCell id="body-class-Seat" parent="class-Seat" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- rowName: String&lt;br&gt;- seatNumber: Integer&lt;br&gt;- status: SeatStatus&lt;/div&gt;" vertex="1">
          <mxGeometry height="80" width="520" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-Seat" parent="class-Seat" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="520" y="120" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-Seat" parent="class-Seat" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ hold(item: TicketHoldItem): void&lt;br&gt;+ release(item: TicketHoldItem): void&lt;br&gt;+ markSold(item: TicketHoldItem): void&lt;br&gt;+ returnToInventory(ticket: Ticket): void&lt;/div&gt;" vertex="1">
          <mxGeometry height="100" width="520" y="121" as="geometry" />
        </mxCell>
        <mxCell id="class-Settlement" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#CFFAFE;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="Settlement" vertex="1">
          <mxGeometry height="381" width="580" x="2834" y="80" as="geometry" />
        </mxCell>
        <mxCell id="body-class-Settlement" parent="class-Settlement" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- status: SettlementStatus&lt;br&gt;- grossRevenue: Money&lt;br&gt;- totalRefund: Money&lt;br&gt;- totalCommission: Money&lt;br&gt;- /netPayable: Money&lt;br&gt;- paidAmount: Money&lt;br&gt;- pendingAmount: Money&lt;br&gt;- /availableToPay: Money&lt;/div&gt;" vertex="1">
          <mxGeometry height="180" width="580" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-Settlement" parent="class-Settlement" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="580" y="220" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-Settlement" parent="class-Settlement" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ recalculate(grossRevenue: Money, totalRefund: Money,&lt;br&gt;&amp;nbsp;&amp;nbsp;&amp;nbsp;&amp;nbsp;totalCommission: Money): void&lt;br&gt;+ confirm(now: Instant): void&lt;br&gt;+ beginPayout(payoutId: Identifier, amount: Money): void&lt;br&gt;+ recordPayoutResult(payoutId: Identifier, amount: Money,&lt;br&gt;&amp;nbsp;&amp;nbsp;&amp;nbsp;&amp;nbsp;result: PayoutResult, reference: String [0..1]): void&lt;/div&gt;" vertex="1">
          <mxGeometry height="160" width="580" y="221" as="geometry" />
        </mxCell>
        <mxCell id="class-TicketHold" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#FEF3C7;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="TicketHold" vertex="1">
          <mxGeometry height="241" width="520" x="2160" y="1499" as="geometry" />
        </mxCell>
        <mxCell id="body-class-TicketHold" parent="class-TicketHold" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- buyer: User&lt;br&gt;- event: Event&lt;br&gt;- expiresAt: Instant&lt;br&gt;- status: HoldStatus&lt;/div&gt;" vertex="1">
          <mxGeometry height="100" width="520" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-TicketHold" parent="class-TicketHold" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="520" y="140" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-TicketHold" parent="class-TicketHold" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ isExpired(now: Instant): Boolean&lt;br&gt;+ isActive(now: Instant): Boolean&lt;br&gt;+ release(): void&lt;br&gt;+ consume(now: Instant): void&lt;/div&gt;" vertex="1">
          <mxGeometry height="100" width="520" y="141" as="geometry" />
        </mxCell>
        <mxCell id="class-TicketHoldItem" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#FEF3C7;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="TicketHoldItem" vertex="1">
          <mxGeometry height="181" width="520" x="3112" y="1520" as="geometry" />
        </mxCell>
        <mxCell id="body-class-TicketHoldItem" parent="class-TicketHoldItem" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- zone: Zone&lt;br&gt;- seat: Seat [0..1]&lt;br&gt;- quantity: Integer&lt;br&gt;- unitPrice: Money&lt;/div&gt;" vertex="1">
          <mxGeometry height="100" width="520" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-TicketHoldItem" parent="class-TicketHoldItem" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="520" y="140" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-TicketHoldItem" parent="class-TicketHoldItem" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ lineTotal(): Money&lt;/div&gt;" vertex="1">
          <mxGeometry height="40" width="520" y="141" as="geometry" />
        </mxCell>
        <mxCell id="class-Order" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#EDE9FE;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="Order" vertex="1">
          <mxGeometry height="401" width="520" x="660" y="1480" as="geometry" />
        </mxCell>
        <mxCell id="body-class-Order" parent="class-Order" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- buyer: User&lt;br&gt;- event: Event&lt;br&gt;- hold: TicketHold&lt;br&gt;- coupon: Coupon [0..1]&lt;br&gt;- acceptedPayment: Payment [0..1]&lt;br&gt;- orderCode: String&lt;br&gt;- status: OrderStatus&lt;br&gt;- discountAmount: Money&lt;br&gt;- /subtotalAmount: Money&lt;br&gt;- /totalAmount: Money&lt;/div&gt;" vertex="1">
          <mxGeometry height="220" width="520" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-Order" parent="class-Order" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="520" y="260" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-Order" parent="class-Order" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ «create» fromHold(hold: TicketHold, now: Instant): Order&lt;br&gt;+ applyCoupon(coupon: Coupon, now: Instant): void&lt;br&gt;+ removeCoupon(): void&lt;br&gt;+ markPaid(payment: Payment [0..1], now: Instant): void&lt;br&gt;+ cancel(): void&lt;br&gt;+ expire(now: Instant): void&lt;/div&gt;" vertex="1">
          <mxGeometry height="140" width="520" y="261" as="geometry" />
        </mxCell>
        <mxCell id="class-OrderItem" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#EDE9FE;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="OrderItem" vertex="1">
          <mxGeometry height="221" width="580" x="1380" y="2230" as="geometry" />
        </mxCell>
        <mxCell id="body-class-OrderItem" parent="class-OrderItem" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- zone: Zone&lt;br&gt;- seat: Seat [0..1]&lt;br&gt;- zoneNameSnapshot: String&lt;br&gt;- seatLabelSnapshot: String [0..1]&lt;br&gt;- quantity: Integer&lt;br&gt;- unitPrice: Money&lt;/div&gt;" vertex="1">
          <mxGeometry height="140" width="580" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-OrderItem" parent="class-OrderItem" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="580" y="180" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-OrderItem" parent="class-OrderItem" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ lineTotal(): Money&lt;/div&gt;" vertex="1">
          <mxGeometry height="40" width="580" y="181" as="geometry" />
        </mxCell>
        <mxCell id="class-Ticket" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#EDE9FE;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="Ticket" vertex="1">
          <mxGeometry height="261" width="520" x="1410" y="2656" as="geometry" />
        </mxCell>
        <mxCell id="body-class-Ticket" parent="class-Ticket" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- ticketCode: String&lt;br&gt;- /zone: Zone&lt;br&gt;- /seat: Seat [0..1]&lt;br&gt;- status: TicketStatus&lt;br&gt;- paidAmount: Money&lt;/div&gt;" vertex="1">
          <mxGeometry height="120" width="520" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-Ticket" parent="class-Ticket" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="520" y="160" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-Ticket" parent="class-Ticket" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ checkIn(event: Event, now: Instant): void&lt;br&gt;+ markRefundPending(now: Instant): void&lt;br&gt;+ restoreAfterRejection(): void&lt;br&gt;+ markRefunded(): void&lt;/div&gt;" vertex="1">
          <mxGeometry height="100" width="520" y="161" as="geometry" />
        </mxCell>
        <mxCell id="class-Payment" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#EDE9FE;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="Payment" vertex="1">
          <mxGeometry height="261" width="520" x="-95" y="1585" as="geometry" />
        </mxCell>
        <mxCell id="body-class-Payment" parent="class-Payment" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- order: Order&lt;br&gt;- amount: Money&lt;br&gt;- status: PaymentStatus&lt;br&gt;- txnRef: String&lt;br&gt;- transactionNo: String [0..1]&lt;/div&gt;" vertex="1">
          <mxGeometry height="120" width="520" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-Payment" parent="class-Payment" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="520" y="160" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-Payment" parent="class-Payment" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ «create» start(order: Order, txnRef: String): Payment&lt;br&gt;+ markCaptured(transactionNo: String, paidAt: Instant): void&lt;br&gt;+ markFailed(): void&lt;br&gt;+ markUnknown(): void&lt;/div&gt;" vertex="1">
          <mxGeometry height="100" width="520" y="161" as="geometry" />
        </mxCell>
        <mxCell id="class-Refund" parent="1" style="swimlane;html=1;startSize=40;horizontal=1;rounded=0;fillColor=#FFE4E6;swimlaneFillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontFamily=Arial;fontSize=18;fontStyle=1;align=center;strokeWidth=1.5;" value="Refund" vertex="1">
          <mxGeometry height="501" width="650" x="595" y="2526" as="geometry" />
        </mxCell>
        <mxCell id="body-class-Refund" parent="class-Refund" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;- tickets: Ticket [0..*]&lt;br&gt;- payment: Payment [0..1]&lt;br&gt;- amount: Money&lt;br&gt;- purpose: RefundPurpose&lt;br&gt;- reasonType: RefundReason [0..1]&lt;br&gt;- reason: String [0..1]&lt;br&gt;- rejectionReason: String [0..1]&lt;br&gt;- status: RefundStatus&lt;br&gt;- currentAttemptId: Identifier [0..1]&lt;br&gt;- providerReference: String [0..1]&lt;br&gt;- processedAt: Instant [0..1]&lt;/div&gt;" vertex="1">
          <mxGeometry height="240" width="650" y="40" as="geometry" />
        </mxCell>
        <mxCell id="divider-class-Refund" parent="class-Refund" style="shape=rectangle;fillColor=#94A3B8;strokeColor=none;" value="" vertex="1">
          <mxGeometry height="1" width="650" y="280" as="geometry" />
        </mxCell>
        <mxCell id="methods-class-Refund" parent="class-Refund" style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize=14;fontColor=#1E293B;spacingLeft=13;spacingRight=10;spacingTop=8;" value="&lt;div style=&quot;line-height:20px&quot;&gt;+ «create» requestForTickets(order: Order, tickets: Ticket [1..*], reasonType: RefundReason,&lt;br&gt;&amp;nbsp;&amp;nbsp;&amp;nbsp;&amp;nbsp;reason: String [0..1], now: Instant): Refund&lt;br&gt;+ «create» createCompensation(payment: Payment, reason: String): Refund&lt;br&gt;+ adoptEventCancellation(): void&lt;br&gt;+ approve(): void&lt;br&gt;+ reject(reason: String): void&lt;br&gt;+ beginAttempt(attemptId: Identifier): void&lt;br&gt;+ recordAttemptResult(attemptId: Identifier, result: RefundTransferResult, reference: String [0..1],&lt;br&gt;&amp;nbsp;&amp;nbsp;&amp;nbsp;&amp;nbsp;processedAt: Instant [0..1]): void&lt;br&gt;+ completeWithoutTransfer(): void&lt;/div&gt;" vertex="1">
          <mxGeometry height="220" width="650" y="281" as="geometry" />
        </mxCell>
        <mxCell id="ownership-2" edge="1" parent="1" source="class-Organization" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;endArrow=none;startArrow=none;startFill=1;startSize=16;strokeWidth=1.6;strokeColor=#475569;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;exitX=0;exitY=0.84;entryX=1;entryY=0.68;exitDx=0;exitDy=0;entryDx=0;entryDy=0;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Coupon" value="coupons">
          <mxGeometry relative="1" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="1380" y="311.9" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-2-owner" connectable="0" parent="ownership-2" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-2-part" connectable="0" parent="ownership-2" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="0.999" as="geometry">
            <mxPoint x="28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-3" edge="1" parent="1" source="class-Organization" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;endArrow=none;startArrow=none;startFill=1;startSize=16;strokeWidth=1.6;strokeColor=#475569;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;exitX=1;exitY=0.5;entryX=0;entryY=0.68;exitDx=0;exitDy=0;entryDx=0;entryDy=0;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-CommissionRule" value="commissionRules">
          <mxGeometry relative="1" x="0.0009" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="2100" y="220.5" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-3-owner" connectable="0" parent="ownership-3" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-3-part" connectable="0" parent="ownership-3" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-18" y="-16" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-4" edge="1" parent="1" source="class-Organization" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;endArrow=none;startArrow=none;startFill=1;startSize=16;strokeWidth=1.6;strokeColor=#475569;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;exitX=1;exitY=0.84;entryX=0;entryY=0.16;exitDx=0;exitDy=0;entryDx=0;entryDy=0;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Event" value="events">
          <mxGeometry relative="1" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="2030" y="316.03999999999996" />
              <mxPoint x="2030" y="736.56" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-4-part" connectable="0" parent="ownership-4" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-4-owner" connectable="0" parent="ownership-4" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-5" edge="1" parent="1" source="class-Event" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;endArrow=none;startArrow=diamond;startFill=1;startSize=16;strokeWidth=1.6;strokeColor=#475569;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;exitX=1;exitY=0.84;exitDx=0;exitDy=0;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Zone" value="zones">
          <mxGeometry relative="1" x="0.272" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="2810.4" y="1104.4" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-5-owner" connectable="0" parent="ownership-5" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-5-part" connectable="0" parent="ownership-5" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="32" y="-28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-6" edge="1" parent="1" source="class-Zone" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;endArrow=none;startArrow=diamond;startFill=1;startSize=16;strokeWidth=1.6;strokeColor=#475569;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;exitX=0.16;exitY=1;entryX=0.16;entryY=0;exitDx=0;exitDy=0;entryDx=0;entryDy=0;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Seat" value="seats">
          <mxGeometry relative="1" as="geometry">
            <mxPoint y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-6-owner" connectable="0" parent="ownership-6" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="32" y="28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-6-part" connectable="0" parent="ownership-6" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="32" y="-28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-7" edge="1" parent="1" source="class-Event" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;endArrow=none;startArrow=none;startFill=1;startSize=16;strokeWidth=1.6;strokeColor=#475569;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Settlement" value="settlement">
          <mxGeometry relative="1" x="0.0003" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="3124" y="772.7" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-7-owner" connectable="0" parent="ownership-7" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="32" y="-28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-7-part" connectable="0" parent="ownership-7" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="0.999" as="geometry">
            <mxPoint x="-25" y="15" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-8" edge="1" parent="1" source="class-TicketHold" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;endArrow=none;startArrow=diamond;startFill=1;startSize=16;strokeWidth=1.6;strokeColor=#475569;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-TicketHoldItem" value="items">
          <mxGeometry relative="1" x="0.2343" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="3231" y="1641" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-8-owner" connectable="0" parent="ownership-8" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-8-part" connectable="0" parent="ownership-8" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="1..*" vertex="1">
          <mxGeometry relative="1" x="0.9993" as="geometry">
            <mxPoint x="-21" y="-17" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-9" edge="1" parent="1" source="class-Order" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;endArrow=none;startArrow=diamond;startFill=1;startSize=16;strokeWidth=1.6;strokeColor=#475569;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-OrderItem" value="items">
          <mxGeometry relative="1" x="0.0006" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="1096.8" y="2265.4" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-9-owner" connectable="0" parent="ownership-9" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="32" y="28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-9-part" connectable="0" parent="ownership-9" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="1..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-10" edge="1" parent="1" source="class-OrderItem" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;endArrow=none;startArrow=diamond;startFill=1;startSize=16;strokeWidth=1.6;strokeColor=#475569;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Ticket" value="tickets">
          <mxGeometry relative="1" x="0.5918" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="1789.6" y="2663.2" />
              <mxPoint x="1789.6" y="2663.2" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-10-owner" connectable="0" parent="ownership-10" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="32" y="28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-10-part" connectable="0" parent="ownership-10" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="0.9992" as="geometry">
            <mxPoint x="32" y="-28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-12" edge="1" parent="1" source="class-Order" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;endArrow=none;startArrow=none;startFill=1;startSize=16;strokeWidth=1.6;strokeColor=#475569;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Refund" value="refunds">
          <mxGeometry relative="1" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-12-owner" connectable="0" parent="ownership-12" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="32" y="28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="ownership-12-part" connectable="0" parent="ownership-12" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="32" y="-28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Organization-class-User-requester" edge="1" parent="1" source="class-Organization" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;exitX=0.16;exitY=1;entryX=0.16;entryY=0;exitDx=0;exitDy=0;entryDx=0;entryDy=0;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-User" value="">
          <mxGeometry relative="1" as="geometry">
            <mxPoint y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Organization-class-User-requester-source" connectable="0" parent="association-class-Organization-class-User-requester" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="32" y="28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Organization-class-User-requester-target" connectable="0" parent="association-class-Organization-class-User-requester" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="requester&lt;br&gt;0..1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="32" y="-58" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Event-class-CommissionRule-commissionRule" edge="1" parent="1" source="class-Event" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-CommissionRule" value="">
          <mxGeometry relative="1" x="0.4989" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Event-class-CommissionRule-commissionRule-source" connectable="0" parent="association-class-Event-class-CommissionRule-commissionRule" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="32" y="-28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Event-class-CommissionRule-commissionRule-target" connectable="0" parent="association-class-Event-class-CommissionRule-commissionRule" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="commissionRule&lt;br&gt;0..1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="66" y="28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-TicketHold-class-User-buyer" edge="1" parent="1" source="class-TicketHold" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;entryX=0.84;entryY=1;entryDx=0;entryDy=0;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-User" value="">
          <mxGeometry relative="1" x="0.0014" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="1816.8" y="1538.6" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-TicketHold-class-User-buyer-source" connectable="0" parent="association-class-TicketHold-class-User-buyer" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-TicketHold-class-User-buyer-target" connectable="0" parent="association-class-TicketHold-class-User-buyer" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="buyer&lt;br&gt;1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="32" y="58" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-TicketHold-class-Event-event" edge="1" parent="1" source="class-TicketHold" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Event" value="">
          <mxGeometry relative="1" x="-0.1877" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-TicketHold-class-Event-event-source" connectable="0" parent="association-class-TicketHold-class-Event-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="32" y="-28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-TicketHold-class-Event-event-target" connectable="0" parent="association-class-TicketHold-class-Event-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="event&lt;br&gt;1" vertex="1">
          <mxGeometry relative="1" x="0.9986" as="geometry">
            <mxPoint x="32" y="58" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-User-buyer" edge="1" parent="1" source="class-Order" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;exitX=0.84;exitY=0;entryX=0;entryY=0.7965891472868216;exitDx=0;exitDy=0;entryDx=0;entryDy=0;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-User" value="">
          <mxGeometry relative="1" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="1096.8" y="958.28" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-User-buyer-source" connectable="0" parent="association-class-Order-class-User-buyer" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="32" y="-28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-User-buyer-target" connectable="0" parent="association-class-Order-class-User-buyer" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="buyer&lt;br&gt;1" vertex="1">
          <mxGeometry relative="1" x="0.9997" as="geometry">
            <mxPoint x="-58" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-Event-event" edge="1" parent="1" source="class-Order" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Event" value="">
          <mxGeometry relative="1" x="0.0286" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="1576.3" y="1544.2" />
              <mxPoint x="1576.3" y="1142" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-Event-event-source" connectable="0" parent="association-class-Order-class-Event-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-Event-event-target" connectable="0" parent="association-class-Order-class-Event-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="event&lt;br&gt;1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-58" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-TicketHold-hold" edge="1" parent="1" source="class-Order" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;exitX=1;exitY=0.84;exitDx=0;exitDy=0;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-TicketHold" value="">
          <mxGeometry relative="1" x="-0.0118" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="2256.8" y="1816.8" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-TicketHold-hold-source" connectable="0" parent="association-class-Order-class-TicketHold-hold" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-TicketHold-hold-target" connectable="0" parent="association-class-Order-class-TicketHold-hold" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="hold&lt;br&gt;1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="32" y="58" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-Coupon-coupon" edge="1" parent="1" source="class-Order" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Coupon" value="">
          <mxGeometry relative="1" x="0.0675" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-Coupon-coupon-source" connectable="0" parent="association-class-Order-class-Coupon-coupon" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="32" y="-28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-Coupon-coupon-target" connectable="0" parent="association-class-Order-class-Coupon-coupon" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="coupon&lt;br&gt;0..1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="32" y="58" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-Payment-payments" edge="1" parent="1" source="class-Order" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Payment" value="">
          <mxGeometry relative="1" x="-0.0007" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="552.29" y="1697.88" />
              <mxPoint x="552.29" y="1697.88" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-Payment-payments-source" connectable="0" parent="association-class-Order-class-Payment-payments" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="order&lt;br&gt;1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Order-class-Payment-payments-target" connectable="0" parent="association-class-Order-class-Payment-payments" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="payments&lt;br&gt;0..*" vertex="1">
          <mxGeometry relative="1" x="0.9974" as="geometry">
            <mxPoint x="16" y="-17" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-TicketHoldItem-class-Zone-zone" edge="1" parent="1" source="class-TicketHoldItem" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;entryX=1;entryY=0.68;entryDx=0;entryDy=0;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Zone" value="">
          <mxGeometry relative="1" x="-0.2083" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="3340.3" y="2489.1" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-TicketHoldItem-class-Zone-zone-source" connectable="0" parent="association-class-TicketHoldItem-class-Zone-zone" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="32" y="28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-TicketHoldItem-class-Zone-zone-target" connectable="0" parent="association-class-TicketHoldItem-class-Zone-zone" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="zone&lt;br&gt;1" vertex="1">
          <mxGeometry relative="1" x="0.9995" as="geometry">
            <mxPoint x="58" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-TicketHoldItem-class-Seat-seat" edge="1" parent="1" source="class-TicketHoldItem" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Seat" value="">
          <mxGeometry relative="1" x="-0.1612" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="3556.3" y="2775.9" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-TicketHoldItem-class-Seat-seat-source" connectable="0" parent="association-class-TicketHoldItem-class-Seat-seat" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="32" y="28" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-TicketHoldItem-class-Seat-seat-target" connectable="0" parent="association-class-TicketHoldItem-class-Seat-seat" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="seat&lt;br&gt;0..1" vertex="1">
          <mxGeometry relative="1" x="0.9991" as="geometry">
            <mxPoint x="58" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-OrderItem-class-Zone-zone" edge="1" parent="1" source="class-OrderItem" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Zone" value="">
          <mxGeometry relative="1" x="0.0003" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="2232.8" y="2308" />
              <mxPoint x="2232.8" y="2308" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-OrderItem-class-Zone-zone-source" connectable="0" parent="association-class-OrderItem-class-Zone-zone" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-OrderItem-class-Zone-zone-target" connectable="0" parent="association-class-OrderItem-class-Zone-zone" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="zone&lt;br&gt;1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-58" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-OrderItem-class-Seat-seat" edge="1" parent="1" source="class-OrderItem" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Seat" value="">
          <mxGeometry relative="1" x="0.4249" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="2185.7" y="2415.6" />
              <mxPoint x="2185.7" y="2875.5" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-OrderItem-class-Seat-seat-source" connectable="0" parent="association-class-OrderItem-class-Seat-seat" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="28" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-OrderItem-class-Seat-seat-target" connectable="0" parent="association-class-OrderItem-class-Seat-seat" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="seat&lt;br&gt;0..1" vertex="1">
          <mxGeometry relative="1" x="0.9993" as="geometry">
            <mxPoint x="-58" y="-18" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Refund-class-Ticket-tickets" edge="1" parent="1" source="class-Refund" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Ticket" value="">
          <mxGeometry relative="1" x="0.2665" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="920" y="3090.4" />
              <mxPoint x="1672.8" y="3090.4" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Refund-class-Ticket-tickets-source" connectable="0" parent="association-class-Refund-class-Ticket-tickets" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="23" y="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Refund-class-Ticket-tickets-target" connectable="0" parent="association-class-Refund-class-Ticket-tickets" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="tickets&lt;br&gt;0..*" vertex="1">
          <mxGeometry relative="1" x="0.9976" as="geometry">
            <mxPoint x="32" y="58" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Refund-class-Payment-payment" edge="1" parent="1" source="class-Refund" style="edgeStyle=segmentEdgeStyle;rounded=0;html=1;startArrow=none;endArrow=none;strokeColor=#475569;strokeWidth=1.6;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;jettySize=0;jumpStyle=arc;jumpSize=8;" target="class-Payment" value="">
          <mxGeometry relative="1" x="-0.3276" as="geometry">
            <mxPoint y="-18" as="offset" />
            <Array as="points">
              <mxPoint x="313.7" y="2571.3" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Refund-class-Payment-payment-source" connectable="0" parent="association-class-Refund-class-Payment-payment" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-22" y="-30" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="association-class-Refund-class-Payment-payment-target" connectable="0" parent="association-class-Refund-class-Payment-payment" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontFamily=Arial;fontSize=14;labelBackgroundColor=#FFFFFF;" value="payment&lt;br&gt;0..1" vertex="1">
          <mxGeometry relative="1" x="0.9984" as="geometry">
            <mxPoint x="41" y="23" as="offset" />
          </mxGeometry>
        </mxCell>
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>
