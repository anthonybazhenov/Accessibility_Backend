import argparse
import os
import time
from typing import Optional

from openai import OpenAI


def upload_file(client: OpenAI, path: str) -> str:
    """Upload a JSONL file for fine-tuning and return the file id."""
    with open(path, "rb") as f:
        uploaded = client.files.create(file=f, purpose="fine-tune")
    print(f"Uploaded {path}")
    print(f"File id: {uploaded.id}")
    return uploaded.id


def create_job(client: OpenAI, training_file_id: str, base_model: str) -> str:
    """Create a fine-tuning job and return the job id."""
    job = client.fine_tuning.jobs.create(training_file=training_file_id, model=base_model)
    print(f"Created fine-tuning job: {job.id}")
    print(f"Base model: {base_model}")
    return job.id


def wait_for_job(
    client: OpenAI,
    job_id: str,
    poll_interval: float = 10.0,
) -> Optional[str]:
    """
    Poll the fine-tuning job until it finishes.
    Returns the fine-tuned model name when succeeded, otherwise None.
    """
    print(f"Waiting for job {job_id}...")
    while True:
        job = client.fine_tuning.jobs.retrieve(job_id)
        status = job.status
        print(f"Status: {status}")
        if status == "succeeded":
            ft_model = job.fine_tuned_model
            print("Fine-tuning succeeded.")
            print(f"Fine-tuned model id: {ft_model}")
            return ft_model
        if status in {"failed", "cancelled"}:
            print(f"Fine-tuning did not succeed (status={status}).")
            return None
        time.sleep(poll_interval)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Upload a JSONL file and run an OpenAI fine-tuning job."
    )
    parser.add_argument(
        "--file",
        default="training/openai_html_remediation.jsonl",
        help="Path to the JSONL training file (default: training/openai_html_remediation.jsonl)",
    )
    parser.add_argument(
        "--base-model",
        default="gpt-4.1-mini-2025-04-14",
        help="Base model for fine-tuning",
    )
    parser.add_argument(
        "--api-key",
        help="OpenAI API key. If omitted, OPENAI_API_KEY environment variable will be used.",
    )
    parser.add_argument(
        "--no-wait",
        action="store_true",
        help="Do not wait for the job to finish; just print job id.",
    )

    args = parser.parse_args()

    # Prefer explicit API key if provided, otherwise fall back to environment variable.
    api_key = args.api_key or os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError(
            "OpenAI API key is not set. Pass --api-key YOUR_KEY or set OPENAI_API_KEY in the environment."
        )

    client = OpenAI(api_key=api_key)

    training_file_id = upload_file(client, args.file)
    job_id = create_job(client, training_file_id, args.base_model)

    print("\n---")
    print("Next steps:")
    print(f"- Fine-tuning job id: {job_id}")
    print("- You can re-run this script with --no-wait to just create jobs quickly.")
    print("- Or let it wait here until the job finishes.")

    if args.no_wait:
        return

    ft_model = wait_for_job(client, job_id)
    if ft_model:
        print("\nSet this as your OPENAI_MODEL:")
        print(f'export OPENAI_MODEL="{ft_model}"')


if __name__ == "__main__":
    main()

