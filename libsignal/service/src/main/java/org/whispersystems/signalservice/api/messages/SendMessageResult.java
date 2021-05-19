package org.whispersystems.signalservice.api.messages;


import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;

public class SendMessageResult {

  private final SignalServiceAddress   address;
  private final Success                success;
  private final boolean                networkFailure;
  private final boolean                unregisteredFailure;
  private final IdentityFailure        identityFailure;
  private final ProofRequiredException proofRequiredFailure;

  public static SendMessageResult success(SignalServiceAddress address, boolean unidentified, boolean needsSync, long duration) {
    return new SendMessageResult(address, new Success(unidentified, needsSync, duration), false, false, null, null);
  }

  public static SendMessageResult networkFailure(SignalServiceAddress address) {
    return new SendMessageResult(address, null, true, false, null, null);
  }

  public static SendMessageResult unregisteredFailure(SignalServiceAddress address) {
    return new SendMessageResult(address, null, false, true, null, null);
  }

  public static SendMessageResult identityFailure(SignalServiceAddress address, IdentityKey identityKey) {
    return new SendMessageResult(address, null, false, false, new IdentityFailure(identityKey), null);
  }

  public static SendMessageResult proofRequiredFailure(SignalServiceAddress address, ProofRequiredException proofRequiredException) {
    return new SendMessageResult(address, null, false, false, null, proofRequiredException);
  }

  public SignalServiceAddress getAddress() {
    return address;
  }

  public Success getSuccess() {
    return success;
  }

  public boolean isNetworkFailure() {
    return networkFailure || proofRequiredFailure != null;
  }

  public boolean isUnregisteredFailure() {
    return unregisteredFailure;
  }

  public IdentityFailure getIdentityFailure() {
    return identityFailure;
  }

  public ProofRequiredException getProofRequiredFailure() {
    return proofRequiredFailure;
  }

  private SendMessageResult(SignalServiceAddress address,
                            Success success,
                            boolean networkFailure,
                            boolean unregisteredFailure,
                            IdentityFailure identityFailure,
                            ProofRequiredException proofRequiredFailure)
  {
    this.address             = address;
    this.success             = success;
    this.networkFailure      = networkFailure;
    this.unregisteredFailure = unregisteredFailure;
    this.identityFailure     = identityFailure;
    this.proofRequiredFailure = proofRequiredFailure;
  }

  public static class Success {
    private final boolean unidentified;
    private final boolean needsSync;
    private final long    duration;

    private Success(boolean unidentified, boolean needsSync, long duration) {
      this.unidentified = unidentified;
      this.needsSync    = needsSync;
      this.duration     = duration;
    }

    public boolean isUnidentified() {
      return unidentified;
    }

    public boolean isNeedsSync() {
      return needsSync;
    }

    public long getDuration() {
      return duration;
    }
  }

  public static class IdentityFailure {
    private final IdentityKey identityKey;

    private IdentityFailure(IdentityKey identityKey) {
      this.identityKey = identityKey;
    }

    public IdentityKey getIdentityKey() {
      return identityKey;
    }
  }



}
