### 2.4 Ring Design Evaluation and Workshop Recommendation

*Trigger*: 
- A Member saves or evaluates a valid workshop-ring configuration with catalogue-controlled material, shape, gemstones, attachments, engraving and geometry inputs.

*End condition*: 


- The server validates the options, calculates the advisory difficulty, estimated weight and estimated values, and returns the rules version and evaluation timestamp. The result is not a final quote and does not mutate workshop price, payment or booking state.

*Alternative end conditions*:

 - If an option or configuration is invalid or incompatible, the server rejects the evaluation and returns a safe validation reason; it does not auto-reject a custom-manufacturing request.

The current difficulty formula and catalogue coefficients are documented in the Feature-012 formula reference. Under the proposed Admin-owned policy, workshop recommendation is advisory and server-owned: a score above the configured too-risky cutoff yields no workshop recommendation and explains the consultation/custom-design path; otherwise a score above the Wax Casting cutoff recommends Wax Casting with a two-session/two-day note; otherwise gemstone quantity above the Silver Clay cutoff recommends Silver Clay; otherwise the applicable Signature workshop is recommended. All general workshop discovery actions remain visible. Existing saved evaluations retain their recorded policy version and result.
 
{{r3-algorithm width=100% align=center}}
